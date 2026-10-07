package dev.dynamiq.talli.mcp.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.Role;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpEventPersistenceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String secret = "whsec_" + Base64.getEncoder().encodeToString(new byte[32]);
    private McpEventProperties properties;
    private UserRepository users;
    private McpCallbackTransport transport;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private McpEventService service;
    private McpEventDispatcher dispatcher;
    private User user;
    private Authentication principal;

    @BeforeEach
    void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES(1), (2)");
        // H2 has no partial indexes. Execute the actual migration with only index predicates removed.
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V51__mcp_email_events.sql"))
                .replace(" WHERE active;", ";").replace(" WHERE status IN ('pending', 'processing');", ";");
        for (String statement : migration.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        properties = new McpEventProperties();
        properties.setEnabled(true);
        users = mock(UserRepository.class);
        transport = mock(McpCallbackTransport.class);
        user = new User(); user.setId(1L); user.setEmail("admin@example.com"); user.setEnabled(true);
        Role admin = new Role(); admin.setName("admin"); user.setRoles(Set.of(admin));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        principal = new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of());
        when(transport.post(anyString(), anyString(), anyString(), any(), anyString(), nullable(String.class))).thenAnswer(invocation -> {
            JsonNode body = mapper.readTree((byte[]) invocation.getArgument(3));
            if (body.has("challenge")) return new McpCallbackTransport.Response(200,
                    mapper.createObjectNode().put("challenge", body.path("challenge").asText()).toString().getBytes(StandardCharsets.UTF_8));
            return new McpCallbackTransport.Response(204, new byte[0]);
        });
        service = new McpEventService(properties, users, jdbc, mapper, transport);
        dispatcher = new McpEventDispatcher(properties, jdbc, users, transport, transactions);
    }
    private ObjectNode params() {
        ObjectNode params = mapper.createObjectNode().put("name", "email.received");
        params.putObject("arguments").put("recipient", "theo@dynamiq.dev");
        params.putObject("delivery").put("mode", "webhook").put("url", "https://callback.example/events").put("secret", secret);
        return params;
    }
    private Email email(String recipient) {
        Email email = new Email(); email.setId(20L); email.setResendId("stable-resend-message");
        email.setDirection("in"); email.setToAddress(recipient); email.setFromAddress("sender@example.com");
        email.setSubject("Subject"); email.setBody("Untrusted email body is excluded from the event");
        email.setReceivedAt(LocalDateTime.of(2026, 10, 7, 12, 0));
        return email;
    }
    private Map<String, Object> subscription() { return jdbc.queryForMap("SELECT * FROM mcp_event_subscriptions"); }
    private Map<String, Object> outbox() { return jdbc.queryForMap("SELECT * FROM mcp_event_outbox"); }
    private void due() { jdbc.update("UPDATE mcp_event_outbox SET next_attempt_at = ?", McpEventService.offset(Instant.now().minusSeconds(60))); }

    @Test
    void disabledIsReadOnlyAndCatalogRemainsAvailable() {
        properties.setEnabled(false);
        assertThat(service.catalog(principal).path("events").size()).isEqualTo(1);
        assertThatThrownBy(() -> service.subscribe(principal, params())).isInstanceOf(McpEventException.class)
                .extracting("reason").isEqualTo("disabled");
        service.enqueue(email("theo@dynamiq.dev")); dispatcher.dispatch();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_outbox", Integer.class)).isZero();
        verifyNoInteractions(transport);
    }
    @Test
    void persistsIdentityRefreshAndRotationAcrossServiceInstances() throws Exception {
        JsonNode first = service.subscribe(principal, params().put("ttlMs", 60_000));
        Instant initialExpiry = McpEventService.instant(subscription().get("expires_at"));
        var restarted = new McpEventService(properties, users, jdbc, mapper, transport);
        ObjectNode refreshed = params().put("ttlMs", 120_000);
        ((ObjectNode) refreshed.path("arguments")).put("recipient", " THEO@DYNAMIQ.DEV ");
        assertThat(restarted.subscribe(principal, refreshed).path("id")).isEqualTo(first.path("id"));
        assertThat(McpEventService.instant(subscription().get("expires_at"))).isAfter(initialExpiry);
        verify(transport, times(1)).post(anyString(), anyString(), anyString(), any(), anyString(), nullable(String.class));
        String replacement = "whsec_" + Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        ((ObjectNode) refreshed.path("delivery")).put("secret", replacement);
        restarted.subscribe(principal, refreshed);
        assertThat(subscription()).containsEntry("signing_secret", replacement).containsEntry("previous_secret", secret);
        assertThat(McpEventService.instant(subscription().get("rotation_until"))).isAfter(Instant.now());
        verify(transport, times(2)).post(anyString(), anyString(), anyString(), any(), anyString(), nullable(String.class));
        restarted.enqueue(email("theo@dynamiq.dev")); dispatcher.dispatch();
        verify(transport).post(anyString(), eq(first.path("id").asText()), startsWith("evt_"), any(), eq(replacement), eq(secret));
    }
    @Test
    void exactRecipientAndPersistentUniqueProviderIdentityPreventDuplicateWakeups() throws Exception {
        service.subscribe(principal, params());
        service.enqueue(email("other@dynamiq.dev"));
        service.enqueue(email("not-theo@dynamiq.dev"));
        Email copy = email("theo@dynamiq.dev"); copy.setCopyOfEmailId(5L); service.enqueue(copy);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_outbox", Integer.class)).isZero();
        service.enqueue(email("someone@example.com, THEO@DYNAMIQ.DEV"));
        Email duplicate = email("theo@dynamiq.dev"); duplicate.setId(21L);
        new McpEventService(properties, users, jdbc, mapper, transport).enqueue(duplicate);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_outbox", Integer.class)).isEqualTo(1);
        String payload = (String) outbox().get("payload");
        assertThat(payload).doesNotContain("Untrusted email body");
        assertThat(mapper.readTree(payload).path("data").path("email_id").asLong()).isEqualTo(20L);
        dispatcher.dispatch(); dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "delivered").containsEntry("attempts", 1);
        verify(transport, times(1)).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
    }
    @Test
    void expiryUnsubscribeAndRevokedAccessCancelQueuedEvents() {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        jdbc.update("UPDATE mcp_event_subscriptions SET expires_at = ?", McpEventService.offset(Instant.now().minusSeconds(1)));
        dispatcher.dispatch(); assertThat(outbox()).containsEntry("status", "cancelled");
        service.subscribe(principal, params());
        jdbc.update("UPDATE mcp_event_outbox SET status = 'pending'");
        user.setEnabled(false); dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "cancelled");
        user.setEnabled(true); service.subscribe(principal, params());
        jdbc.update("UPDATE mcp_event_outbox SET status = 'pending'");
        properties.setAllowedRecipient("another@dynamiq.dev"); dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "cancelled");
        properties.setAllowedRecipient("theo@dynamiq.dev"); service.subscribe(principal, params());
        service.unsubscribe(principal, params()); service.unsubscribe(principal, params());
        assertThat(subscription()).containsEntry("active", false);
    }
    @Test
    void transientRetriesKeepExactPayloadAndStopAfterEightAttempts() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        doReturn(new McpCallbackTransport.Response(503, new byte[0])).when(transport)
                .post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        String payload = (String) outbox().get("payload"), eventId = (String) outbox().get("event_id");
        for (int attempt = 1; attempt <= 8; attempt++) {
            due(); dispatcher.dispatch();
            assertThat(outbox()).containsEntry("attempts", attempt).containsEntry("payload", payload).containsEntry("event_id", eventId);
            assertThat(McpEventService.instant(outbox().get("next_attempt_at"))).isAfter(Instant.now());
        }
        assertThat(outbox()).containsEntry("status", "failed");
        due(); dispatcher.dispatch(); assertThat(outbox()).containsEntry("attempts", 8);
    }
    @Test
    void goneAndOversizedAreTerminal() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        doReturn(new McpCallbackTransport.Response(413, new byte[0])).when(transport)
                .post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        dispatcher.dispatch(); assertThat(outbox()).containsEntry("status", "failed");
        jdbc.update("UPDATE mcp_event_outbox SET status = 'pending'"); due();
        doReturn(new McpCallbackTransport.Response(410, new byte[0])).when(transport)
                .post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        dispatcher.dispatch(); assertThat(outbox()).containsEntry("status", "failed");
        assertThat(subscription()).containsEntry("active", false);
    }
    @Test
    void verificationFailureNeverPersistsAndPermissionsAreAccountScoped() throws Exception {
        doReturn(new McpCallbackTransport.Response(200, "{\"challenge\":\"wrong\"}".getBytes(StandardCharsets.UTF_8))).when(transport)
                .post(anyString(), anyString(), anyString(), any(), anyString(), nullable(String.class));
        assertThatThrownBy(() -> service.subscribe(principal, params())).isInstanceOf(McpEventException.class).extracting("code").isEqualTo(-32015);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_subscriptions", Integer.class)).isZero();
        user.setRoles(Set.of());
        assertThatThrownBy(() -> service.catalog(principal)).isInstanceOf(McpEventException.class).extracting("reason").isEqualTo("forbidden");
    }
    @Test
    void slowCallbackHoldsNoDatabaseTransactionAndDoesNotBlockIntake() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var hadTransaction = new java.util.concurrent.atomic.AtomicBoolean();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            hadTransaction.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("test callback timed out");
            }
            return new McpCallbackTransport.Response(204, new byte[0]);
        }).when(transport).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var dispatch = executor.submit(dispatcher::dispatch);
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Email fresh = email("theo@dynamiq.dev"); fresh.setResendId("arrives-during-slow-callback");
            executor.submit(() -> transactions.executeWithoutResult(status -> service.enqueue(fresh)))
                    .get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_outbox", Integer.class)).isEqualTo(2);
            assertThat(hadTransaction).isFalse();
            release.countDown();
            dispatch.get(3, java.util.concurrent.TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test
    void cancelledProcessingCannotBeRevivedOrDeactivateImmediateResubscription() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("test callback timed out");
            return new McpCallbackTransport.Response(410, new byte[0]);
        }).when(transport).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var dispatch = executor.submit(dispatcher::dispatch);
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> transactions.executeWithoutResult(status -> service.unsubscribe(principal, params())))
                    .get(1, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(outbox()).containsEntry("status", "cancelled");
            service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
            release.countDown(); dispatch.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(outbox()).containsEntry("status", "cancelled").containsEntry("attempts", 1);
            assertThat(subscription()).containsEntry("active", true);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test
    void expiredProcessingLeaseRecoversAcrossRestartWithoutChangingEventIdentity() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        String eventId = (String) outbox().get("event_id"), payload = (String) outbox().get("payload");
        jdbc.update("UPDATE mcp_event_outbox SET status = 'processing', attempts = 3, next_attempt_at = ?",
                McpEventService.offset(Instant.now().plusSeconds(60)));
        dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "processing").containsEntry("attempts", 3);
        verify(transport, never()).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        due();
        new McpEventDispatcher(properties, jdbc, users, transport, transactions).dispatch();
        assertThat(outbox()).containsEntry("status", "delivered").containsEntry("attempts", 4)
                .containsEntry("event_id", eventId).containsEntry("payload", payload);
    }
    @Test
    void crashedFinalAttemptStopsAfterLeaseExpires() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        jdbc.update("UPDATE mcp_event_outbox SET status = 'processing', attempts = 8"); due();
        dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "failed").containsEntry("attempts", 8);
        verify(transport, never()).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
    }
    @Test
    void staleAttemptCannotOverwriteAReclaimedSuccessfulDelivery() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("test callback timed out");
                return new McpCallbackTransport.Response(503, new byte[0]);
            }
            return new McpCallbackTransport.Response(204, new byte[0]);
        }).when(transport).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var oldAttempt = executor.submit(dispatcher::dispatch);
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            due(); // Simulate a worker stall beyond its lease while another instance recovers it.
            new McpEventDispatcher(properties, jdbc, users, transport, transactions).dispatch();
            assertThat(outbox()).containsEntry("status", "delivered").containsEntry("attempts", 2);
            release.countDown(); oldAttempt.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(outbox()).containsEntry("status", "delivered").containsEntry("attempts", 2).containsEntry("last_status", 204);
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test
    void rechecksOwnerAccessAfterClaimBeforeSending() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        when(users.findById(1L)).thenReturn(Optional.of(user), Optional.empty());
        dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "cancelled");
        verify(transport, never()).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
    }
    @Test
    void unsubscribeWaitsForInflightIntakeAndCancelsItsCommittedQueue() throws Exception {
        service.subscribe(principal, params());
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var started = new java.util.concurrent.CountDownLatch(1);
        var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try {
            transactions.executeWithoutResult(status -> {
                service.enqueue(email("theo@dynamiq.dev"));
                future.set(executor.submit(() -> {
                    started.countDown();
                    transactions.executeWithoutResult(tx -> service.unsubscribe(principal, params()));
                }));
                try {
                    assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> future.get().get(100, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            });
            future.get().get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(outbox()).containsEntry("status", "cancelled");
            assertThat(subscription()).containsEntry("active", false);
        } finally { executor.shutdownNow(); }
    }
    @Test
    void immediateResubscribeNeverRevivesCancelledNotifications() throws Exception {
        service.subscribe(principal, params()); service.enqueue(email("theo@dynamiq.dev"));
        service.unsubscribe(principal, params());
        assertThat(outbox()).containsEntry("status", "cancelled");
        service.subscribe(principal, params());
        service.enqueue(email("theo@dynamiq.dev")); // Provider retry of the stopped event stays deduplicated.
        dispatcher.dispatch();
        assertThat(outbox()).containsEntry("status", "cancelled");
        verify(transport, never()).post(anyString(), anyString(), startsWith("evt_"), any(), anyString(), nullable(String.class));
        Email fresh = email("theo@dynamiq.dev"); fresh.setResendId("new-message-after-resubscribe");
        service.enqueue(fresh); due(); dispatcher.dispatch();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_outbox WHERE status = 'delivered'", Integer.class)).isEqualTo(1);
    }
    @Test
    void subscriptionIdentityAndUnsubscribeAreBoundToAccount() {
        JsonNode first = service.subscribe(principal, params());
        User second = new User(); second.setId(2L); second.setEmail("second@example.com");
        second.setEnabled(true); second.setRoles(user.getRoles());
        when(users.findByEmail(second.getEmail())).thenReturn(Optional.of(second));
        Authentication other = new UsernamePasswordAuthenticationToken(second.getEmail(), null, List.of());
        JsonNode secondSubscription = service.subscribe(other, params());
        assertThat(secondSubscription.path("id")).isNotEqualTo(first.path("id"));
        service.unsubscribe(principal, params());
        assertThat(jdbc.queryForObject("SELECT active FROM mcp_event_subscriptions WHERE id = ?", Boolean.class,
                secondSubscription.path("id").asText())).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM mcp_event_subscriptions WHERE id = ?", Boolean.class,
                first.path("id").asText())).isFalse();
    }
    @Test
    void enqueueRollsBackWithOuterEmailTransaction() {
        service.subscribe(principal, params());
        transactions.executeWithoutResult(status -> { service.enqueue(email("theo@dynamiq.dev")); status.setRollbackOnly(); });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mcp_event_outbox", Integer.class)).isZero();
    }
}

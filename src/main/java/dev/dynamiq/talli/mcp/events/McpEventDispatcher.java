package dev.dynamiq.talli.mcp.events;

import dev.dynamiq.talli.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class McpEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(McpEventDispatcher.class);
    private static final int MAX_ATTEMPTS = 8;
    private static final int LEASE_SECONDS = 60;
    private final McpEventProperties properties;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final McpCallbackTransport transport;
    private final TransactionTemplate transactions;
    public McpEventDispatcher(McpEventProperties properties, JdbcTemplate jdbc, UserRepository users,
                              McpCallbackTransport transport, TransactionTemplate transactions) {
        this.properties = properties;
        this.jdbc = jdbc;
        this.users = users;
        this.transport = transport;
        this.transactions = transactions;
    }
    @Scheduled(fixedDelayString = "${app.mcp.events.dispatch-delay-ms:5000}", scheduler = "mcpEventTaskScheduler")
    public void dispatch() {
        for (int i = 0; i < 10 && properties.isEnabled(); i++) {
            try {
                Claim claim = transactions.execute(status -> claimNext());
                if (claim == null) return;
                if (claim.attempt == 0) continue;
                Delivery delivery = transactions.execute(status -> prepare(claim));
                if (delivery == null) continue;
                // No database transaction or row lock spans the network call.
                Outcome outcome = send(delivery);
                transactions.executeWithoutResult(status -> finish(delivery.claim, outcome));
            } catch (RuntimeException e) {
                // A committed processing lease recovers on the next run after its deadline.
                // Callback secrets, URLs, and email metadata are intentionally absent from logs.
                log.warn("MCP event dispatch could not complete ({})", e.getClass().getSimpleName());
                return;
            }
        }
    }
    private Claim claimNext() {
        if (!properties.isEnabled()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, subscription_id FROM mcp_event_outbox
                WHERE status IN ('pending', 'processing') AND next_attempt_at <= ?
                ORDER BY next_attempt_at, id LIMIT 1
                """, McpEventService.offset(Instant.now()));
        if (rows.isEmpty()) return null;
        Map<String, Object> candidate = rows.getFirst();
        long id = ((Number) candidate.get("id")).longValue();
        String subscriptionId = (String) candidate.get("subscription_id");
        Claim skipped = new Claim(id, subscriptionId, 0);
        List<Map<String, Object>> subscriptions = lockSubscription(subscriptionId);
        if (subscriptions.isEmpty()) return skipped;
        // Enqueue, unsubscribe, claims and completion all lock subscription before outbox.
        List<Map<String, Object>> claimed = jdbc.queryForList("""
                SELECT * FROM mcp_event_outbox WHERE id = ? AND status IN ('pending', 'processing') AND next_attempt_at <= ?
                FOR UPDATE SKIP LOCKED
                """, id, McpEventService.offset(Instant.now()));
        if (claimed.isEmpty()) return skipped;
        Map<String, Object> row = claimed.getFirst();
        Instant now = Instant.now();
        if (!allowed(subscriptions.getFirst(), now)) {
            cancelSubscription(subscriptionId);
            return skipped;
        }
        int attempt = ((Number) row.get("attempts")).intValue() + 1;
        if (attempt > MAX_ATTEMPTS) {
            jdbc.update("UPDATE mcp_event_outbox SET status = 'failed' WHERE id = ?", id);
            return skipped;
        }
        jdbc.update("UPDATE mcp_event_outbox SET status = 'processing', attempts = ?, next_attempt_at = ? WHERE id = ?",
                attempt, McpEventService.offset(now.plusSeconds(LEASE_SECONDS)), id);
        return new Claim(id, subscriptionId, attempt);
    }
    private Delivery prepare(Claim claim) {
        if (!properties.isEnabled()) return null;
        List<Map<String, Object>> subscriptions = lockSubscription(claim.subscriptionId);
        if (subscriptions.isEmpty()) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM mcp_event_outbox WHERE id = ? AND status = 'processing' AND attempts = ? AND next_attempt_at > ?
                FOR UPDATE
                """, claim.id, claim.attempt, McpEventService.offset(Instant.now()));
        if (rows.isEmpty()) return null;
        Instant now = Instant.now();
        Map<String, Object> sub = subscriptions.getFirst();
        if (!allowed(sub, now)) {
            cancelSubscription(claim.subscriptionId);
            return null;
        }
        String previous = sub.get("rotation_until") != null && McpEventService.instant(sub.get("rotation_until")).isAfter(now)
                ? (String) sub.get("previous_secret") : null;
        Map<String, Object> row = rows.getFirst();
        return new Delivery(claim, (String) row.get("event_id"), ((String) row.get("payload")).getBytes(StandardCharsets.UTF_8),
                (String) sub.get("callback_url"), (String) sub.get("signing_secret"), previous);
    }
    private Outcome send(Delivery delivery) {
        try {
            McpCallbackTransport.Response response = transport.post(delivery.url, delivery.claim.subscriptionId,
                    delivery.eventId, delivery.payload, delivery.secret, delivery.previousSecret);
            int status = response.status();
            return new Outcome(status >= 200 && status < 300, status == 408 || status == 429 || status >= 500, status);
        } catch (java.io.IOException e) {
            return new Outcome(false, true, null);
        } catch (McpEventException e) {
            // A newly private DNS answer or invalid callback configuration is terminal.
            return new Outcome(false, false, null);
        }
    }
    private void finish(Claim claim, Outcome outcome) {
        if (lockSubscription(claim.subscriptionId).isEmpty()) return;
        String status = outcome.accepted ? "delivered" : outcome.retry && claim.attempt < MAX_ATTEMPTS ? "pending" : "failed";
        long delay = Math.min(3600L, 30L << Math.min(claim.attempt - 1, 7));
        int changed = jdbc.update("""
                UPDATE mcp_event_outbox SET status = ?, last_status = ?, next_attempt_at = ?
                WHERE id = ? AND status = 'processing' AND attempts = ?
                """, status, outcome.status, McpEventService.offset(Instant.now().plusSeconds(delay)), claim.id, claim.attempt);
        // A cancelled or superseded attempt cannot deactivate a renewed subscription.
        if (changed == 1 && Integer.valueOf(410).equals(outcome.status)) cancelSubscription(claim.subscriptionId);
    }
    private List<Map<String, Object>> lockSubscription(String id) {
        return jdbc.queryForList("SELECT * FROM mcp_event_subscriptions WHERE id = ? FOR UPDATE", id);
    }
    private boolean allowed(Map<String, Object> sub, Instant now) {
        return Boolean.TRUE.equals(sub.get("active")) && McpEventService.instant(sub.get("expires_at")).isAfter(now)
                && properties.getAllowedRecipient().trim().equalsIgnoreCase((String) sub.get("recipient"))
                && users.findById(((Number) sub.get("owner_id")).longValue())
                    .filter(user -> Boolean.TRUE.equals(user.getEnabled()) && user.hasRole("admin")).isPresent();
    }
    private void cancelSubscription(String id) {
        jdbc.update("UPDATE mcp_event_subscriptions SET active = FALSE WHERE id = ?", id);
        jdbc.update("UPDATE mcp_event_outbox SET status = 'cancelled' WHERE subscription_id = ? AND status IN ('pending', 'processing')", id);
    }
    private record Claim(long id, String subscriptionId, int attempt) { }
    private record Delivery(Claim claim, String eventId, byte[] payload, String url, String secret, String previousSecret) { }
    private record Outcome(boolean accepted, boolean retry, Integer status) { }
}

package dev.dynamiq.talli.mcp.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class McpEventService {
    static final String EVENT = "email.received";
    static final Duration DEFAULT_TTL = Duration.ofDays(1);
    private final McpEventProperties properties;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final McpCallbackTransport transport;

    public McpEventService(McpEventProperties properties, UserRepository users, JdbcTemplate jdbc,
                           ObjectMapper mapper, McpCallbackTransport transport) {
        this.properties = properties;
        this.users = users;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transport = transport;
    }
    public boolean isEnabled() { return properties.isEnabled(); }

    public JsonNode catalog(Authentication principal) {
        owner(principal);
        ObjectNode event = mapper.createObjectNode();
        event.put("name", EVENT);
        event.put("description", "A new inbound email to " + recipient() + ". Includes bounded metadata; use the email read tools for the complete email.");
        event.putArray("delivery").add("webhook");
        ObjectNode input = event.putObject("inputSchema");
        input.put("type", "object");
        input.putObject("properties").putObject("recipient").put("type", "string").putArray("enum").add(recipient());
        input.putArray("required").add("recipient");
        input.put("additionalProperties", false);
        ObjectNode payload = event.putObject("payloadSchema");
        payload.put("type", "object");
        ObjectNode fields = payload.putObject("properties");
        fields.putObject("email_id").put("type", "integer");
        for (String field : List.of("resend_id", "recipient", "from", "subject", "received_at")) fields.putObject(field).put("type", "string");
        payload.putArray("required").add("email_id").add("resend_id").add("recipient").add("from").add("subject").add("received_at");
        payload.put("additionalProperties", false);
        ObjectNode result = mapper.createObjectNode();
        result.putArray("events").add(event);
        return result;
    }

    @Transactional
    public JsonNode subscribe(Authentication principal, JsonNode params) {
        User owner = owner(principal);
        if (!isEnabled()) throw new McpEventException(-32000, "disabled", "MCP event subscriptions are disabled");
        Identity identity = identity(owner, params);
        String secret = text(params.path("delivery"), "secret");
        StandardWebhookSigner.key(secret);
        long ttl = DEFAULT_TTL.toMillis();
        JsonNode requestedTtl = params.get("ttlMs");
        if (requestedTtl != null && !requestedTtl.isNull()) {
            if (!requestedTtl.isIntegralNumber() || !requestedTtl.canConvertToLong() || requestedTtl.longValue() <= 0)
                throw invalid("ttlMs must be a positive integer or null");
            ttl = Math.min(ttl, requestedTtl.longValue());
        }
        Instant now = Instant.now();
        Instant expires = now.plusMillis(ttl);
        List<Map<String, Object>> old = jdbc.queryForList("SELECT signing_secret, verified_at FROM mcp_event_subscriptions WHERE id = ?", identity.id);
        boolean verified = !old.isEmpty() && secret.equals(old.getFirst().get("signing_secret"))
                && instant(old.getFirst().get("verified_at")).isAfter(now.minusSeconds(300));
        if (!verified) verify(identity, secret);
        jdbc.update("""
                INSERT INTO mcp_event_subscriptions(id, owner_id, recipient, callback_url, signing_secret, expires_at, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, identity.id, owner.getId(), identity.recipient, identity.url, secret,
                offset(expires), offset(now));
        jdbc.update("""
                UPDATE mcp_event_subscriptions SET previous_secret = CASE WHEN signing_secret <> ? THEN signing_secret ELSE previous_secret END,
                rotation_until = CASE WHEN signing_secret <> ? THEN ? ELSE rotation_until END,
                signing_secret = ?, expires_at = ?, verified_at = ?, active = TRUE WHERE id = ?
                """, secret, secret, offset(now.plusSeconds(300)), secret, offset(expires),
                offset(verified ? instant(old.getFirst().get("verified_at")) : now), identity.id);
        return mapper.createObjectNode().put("id", identity.id).put("refreshBefore", expires.toString())
                .putNull("cursor").put("truncated", false);
    }

    @Transactional
    public JsonNode unsubscribe(Authentication principal, JsonNode params) {
        User owner = owner(principal);
        Identity identity = identity(owner, params);
        // Subscription-first locking matches enqueue and dispatch, so cancellation also covers intake races.
        jdbc.update("UPDATE mcp_event_subscriptions SET active = FALSE WHERE id = ? AND owner_id = ?", identity.id, owner.getId());
        jdbc.update("UPDATE mcp_event_outbox SET status = 'cancelled' WHERE subscription_id = ? AND status IN ('pending', 'processing')", identity.id);
        return mapper.createObjectNode();
    }

    /** Participates in the inbound email transaction; failures must cause the provider to retry. */
    @Transactional
    public void enqueue(Email email) {
        if (!isEnabled() || !"in".equals(email.getDirection()) || email.getCopyOfEmailId() != null
                || email.getId() == null || email.getResendId() == null || email.getResendId().isBlank()) return;
        String recipient = recipient();
        if (email.getToAddress() == null || Arrays.stream(email.getToAddress().split(","))
                .map(v -> v.trim().toLowerCase(Locale.ROOT)).noneMatch(recipient::equals)) return;
        Instant occurred = (email.getReceivedAt() == null ? email.getCreatedAt() : email.getReceivedAt()).toInstant(ZoneOffset.UTC);
        String eventId = "evt_" + hash(email.getResendId());
        ObjectNode event = mapper.createObjectNode().put("eventId", eventId).put("name", EVENT).put("timestamp", occurred.toString());
        event.putNull("cursor");
        event.putObject("data").put("email_id", email.getId()).put("resend_id", email.getResendId())
                .put("recipient", recipient).put("from", bounded(email.getFromAddress(), 512))
                .put("subject", bounded(email.getSubject(), 512)).put("received_at", occurred.toString());
        String payload = event.toString();
        if (payload.getBytes(StandardCharsets.UTF_8).length > McpCallbackTransport.MAX_BYTES) throw new IllegalStateException("MCP event exceeds payload limit");
        List<String> subscriptions = jdbc.queryForList("""
                SELECT id FROM mcp_event_subscriptions WHERE active = TRUE AND recipient = ? AND expires_at > ?
                ORDER BY id FOR UPDATE
                """, String.class, recipient, offset(Instant.now()));
        for (String subscription : subscriptions) {
            jdbc.update("""
                    INSERT INTO mcp_event_outbox(subscription_id, event_id, payload, next_attempt_at)
                    VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, subscription, eventId, payload, offset(Instant.now()));
        }
    }

    private void verify(Identity identity, String secret) {
        String challenge = UUID.randomUUID().toString() + UUID.randomUUID();
        byte[] body = mapper.createObjectNode().put("type", "verification").put("challenge", challenge).toString().getBytes(StandardCharsets.UTF_8);
        try {
            McpCallbackTransport.Response response = transport.post(identity.url, identity.id,
                    "msg_verification_" + UUID.randomUUID(), body, secret, null);
            JsonNode reply = response.status() >= 200 && response.status() < 300 ? mapper.readTree(response.body()) : null;
            if (reply == null || !reply.path("challenge").isTextual()
                    || !MessageDigest.isEqual(challenge.getBytes(StandardCharsets.UTF_8), reply.path("challenge").textValue().getBytes(StandardCharsets.UTF_8)))
                throw new McpEventException(-32015, "challenge_failed", "Callback did not verify the challenge");
        } catch (java.net.SocketTimeoutException e) {
            throw new McpEventException(-32015, "timeout", "Callback verification timed out");
        } catch (java.io.IOException e) {
            throw new McpEventException(-32015, "challenge_failed", "Callback verification failed");
        }
    }
    private User owner(Authentication principal) {
        if (principal == null || !principal.isAuthenticated()) throw denied();
        return users.findByEmail(principal.getName()).filter(u -> Boolean.TRUE.equals(u.getEnabled()) && u.hasRole("admin"))
                .orElseThrow(McpEventService::denied);
    }
    private Identity identity(User owner, JsonNode params) {
        if (params == null || !params.isObject() || !EVENT.equals(text(params, "name"))) throw invalid("Unknown event name");
        JsonNode arguments = params.path("arguments");
        if (!arguments.isObject() || arguments.size() != 1) throw invalid("arguments must contain only recipient");
        String recipient = text(arguments, "recipient").trim().toLowerCase(Locale.ROOT);
        if (!recipient().equals(recipient)) throw invalid("Recipient is not available for subscriptions");
        JsonNode delivery = params.path("delivery");
        if (!"webhook".equals(text(delivery, "mode"))) throw invalid("Only webhook delivery is supported");
        String url = text(delivery, "url");
        McpCallbackTransport.validateUrl(url);
        // This event has one string argument. The fixed array is its canonical identity encoding.
        String canonical = mapper.createArrayNode().add(owner.getId()).add(url).add(EVENT).add(recipient).toString();
        return new Identity("sub_" + hash(canonical), recipient, url);
    }
    private String recipient() { return properties.getAllowedRecipient().trim().toLowerCase(Locale.ROOT); }
    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) throw invalid(field + " must be a string");
        return value.textValue();
    }
    private static String bounded(String value, int limit) { return value == null ? "" : value.substring(0, Math.min(value.length(), limit)); }
    private static McpEventException invalid(String message) { return new McpEventException(-32602, "invalid_params", message); }
    private static McpEventException denied() { return new McpEventException(-32001, "forbidden", "An enabled administrator account is required"); }
    static java.time.OffsetDateTime offset(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    static Instant instant(Object value) {
        if (value instanceof java.time.OffsetDateTime date) return date.toInstant();
        if (value instanceof java.sql.Timestamp date) return date.toInstant();
        if (value instanceof Instant date) return date;
        throw new IllegalStateException("Unsupported timestamp representation");
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Identity(String id, String recipient, String url) { }
}

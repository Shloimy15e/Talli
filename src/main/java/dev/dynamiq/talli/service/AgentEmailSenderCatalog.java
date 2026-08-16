package dev.dynamiq.talli.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides the allow-listed sender identities available to authenticated agents. */
@Service
public class AgentEmailSenderCatalog {

    private final EmailSender defaultSender;
    private final Map<String, EmailSender> sendersByAddress;

    public AgentEmailSenderCatalog(
            @Value("${app.mail.from}") String defaultAddress,
            @Value("${app.mail.from-name}") String defaultName,
            @Value("${app.mcp.email.senders:}") String configuredAddresses) {
        this.defaultSender = new EmailSender(
                validEmail(defaultAddress, "app.mail.from"),
                validName(defaultName));

        Map<String, EmailSender> configured = new LinkedHashMap<>();
        configured.put(key(defaultSender.address()), defaultSender);
        if (configuredAddresses != null) {
            for (String address : configuredAddresses.split(",")) {
                if (address.isBlank()) continue;
                EmailSender sender = new EmailSender(
                        validEmail(address, "app.mcp.email.senders"), defaultSender.name());
                configured.putIfAbsent(key(sender.address()), sender);
            }
        }
        this.sendersByAddress = Collections.unmodifiableMap(new LinkedHashMap<>(configured));
    }

    public List<Option> options() {
        return sendersByAddress.values().stream()
                .map(sender -> new Option(sender.address(), sender.name(),
                        sender.address().equalsIgnoreCase(defaultSender.address()),
                        sender.defaultSignatureHtml()))
                .toList();
    }

    EmailSender resolve(String requestedAddress) {
        if (requestedAddress == null || requestedAddress.isBlank()) return defaultSender;

        EmailSender sender = sendersByAddress.get(key(requestedAddress));
        if (sender == null) {
            throw new IllegalArgumentException("sender_email must be one of the configured addresses: "
                    + String.join(", ", sendersByAddress.values().stream()
                    .map(EmailSender::address).toList()));
        }
        return sender;
    }

    private static String key(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private static String validEmail(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(property + " is required");
        }
        String email = value.trim();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(property + " must contain valid email addresses");
        }
        return email;
    }

    private static String validName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.mail.from-name is required");
        }
        String name = value.trim();
        if (name.contains("\r") || name.contains("\n")) {
            throw new IllegalArgumentException("app.mail.from-name must not contain line breaks");
        }
        return name;
    }

    public record Option(String address, String name, boolean defaultSender,
                         String defaultSignatureHtml) {}
}

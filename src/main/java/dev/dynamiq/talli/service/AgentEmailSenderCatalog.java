package dev.dynamiq.talli.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides the allow-listed sender identities available to authenticated agents. */
@Service
public class AgentEmailSenderCatalog {

    private static final List<EmailSender> APPROVED_SENDERS = List.of(
            new EmailSender("info@dynamiq.dev", "Dynamiq Solutions"),
            new EmailSender("billing@dynamiq.dev", "Dynamiq Billing"),
            new EmailSender("finance@dynamiq.dev", "Dynamiq Finance"),
            new EmailSender("support@dynamiq.dev", "Dynamiq Support"),
            new EmailSender("sales@dynamiq.dev", "Dynamiq Sales"));

    private final EmailSender defaultSender;
    private final Map<String, EmailSender> sendersByAddress;

    public AgentEmailSenderCatalog() {
        this.defaultSender = APPROVED_SENDERS.getFirst();
        Map<String, EmailSender> configured = new LinkedHashMap<>();
        for (EmailSender sender : APPROVED_SENDERS) {
            configured.put(key(sender.address()), sender);
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

    public record Option(String address, String name, boolean defaultSender,
                         String defaultSignatureHtml) {}
}

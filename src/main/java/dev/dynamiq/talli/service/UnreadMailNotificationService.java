package dev.dynamiq.talli.service;

import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Sends an inbox reminder without copying the underlying correspondence. */
@Service
public class UnreadMailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(UnreadMailNotificationService.class);

    private final UserRepository users;
    private final EmailRepository emails;
    private final EmailService delivery;
    private final String inboxUrl;

    public UnreadMailNotificationService(UserRepository users, EmailRepository emails,
                                         EmailService delivery,
                                         @Value("${app.base-url}") String baseUrl) {
        this.users = users;
        this.emails = emails;
        this.delivery = delivery;
        this.inboxUrl = baseUrl.replaceAll("/+$", "") + "/emails?folder=inbox";
    }

    public void notifyUnreadAdmins() {
        for (var admin : users.findEnabledByRoleName("admin")) {
            String address = admin.getEmail();
            if (address == null || address.isBlank()) continue;
            address = address.trim();
            String domain = address.substring(address.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
            if (domain.equals("dynamiq.dev") || domain.endsWith(".dynamiq.dev")) continue;

            try {
                long count = emails.countUnreadInboxConversations(admin.getId());
                if (count == 0) continue;
                String summary = count + " unread " + (count == 1 ? "conversation" : "conversations");
                delivery.sendPlain(address, List.of(), "Talli: " + summary,
                        "You have " + summary + " in your Talli inbox.\n\n"
                                + "Open your inbox: " + inboxUrl);
                log.info("Sent unread mail notification to admin id={} ({} conversations)", admin.getId(), count);
            } catch (Exception exception) {
                log.error("Could not send unread mail notification to admin id={}", admin.getId(), exception);
            }
        }
    }
}

package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.EmailMailboxState;
import dev.dynamiq.talli.repository.EmailMailboxStateRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Links local email audit rows using the RFC headers that recipients use for threading. */
@Service
public class EmailThreadService {

    private final EmailRepository emails;

    private final EmailMailboxStateRepository mailboxStates;

    public EmailThreadService(EmailRepository emails, EmailMailboxStateRepository mailboxStates) {
        this.emails = emails;
        this.mailboxStates = mailboxStates;
    }

    /**
     * Prepares the stored headers and root for a received message. Call
     * {@link #ensureThreadRoot(Email)} after the new row has an ID.
     */
    public void prepareInbound(Email email, String messageId, String inReplyTo, String references) {
        email.setMessageId(normalizeMessageId(messageId));
        email.setInReplyTo(normalizeMessageId(inReplyTo));
        email.setReferencesHeader(normalizeReferences(references));
        resolveThreadRoot(email).ifPresent(email::setThreadRootId);
    }

    /** Returns the saved envelope and any RFC headers available for a reply. */
    public ReplyContext replyContext(Long replyToEmailId) {
        if (replyToEmailId == null) throw new IllegalArgumentException("replyToEmailId is required");
        Email parent = emails.findById(replyToEmailId)
                .orElseThrow(() -> new IllegalArgumentException("Email not found: " + replyToEmailId));
        if (parent.getCopyOfEmailId() != null) {
            Long canonicalId = parent.getCopyOfEmailId();
            parent = emails.findById(canonicalId)
                    .orElseThrow(() -> new IllegalArgumentException("Canonical email not found: " + canonicalId));
        }
        String parentMessageId = normalizeMessageId(parent.getMessageId());
        String recipient = normalizeMailbox(replyRecipient(parent));
        if (recipient == null) {
            throw new IllegalStateException("This email has no valid reply recipient.");
        }
        Long rootId = rootId(parent);
        String senderHint = normalizeMailbox(replySenderHint(parent));
        return new ReplyContext(parent.getId(), rootId, parentMessageId,
                parentMessageId == null ? null : appendReference(parent.getReferencesHeader(), parentMessageId),
                parentMessageId == null ? parent.getSubject() : replySubject(parent.getSubject()), recipient, senderHint,
                replyAddresses(parent.getCc(), recipient, senderHint),
                replyAddresses(parent.getBcc(), recipient, senderHint));
    }

    /**
     * Associates a persisted outbound email with its local conversation and
     * stores its Message-ID once Resend reports it. Safe to call before and
     * after the send operation.
     */
    @Transactional
    public void prepareOutgoing(Email email, Long replyToEmailId, String messageId) {
        if (email.getId() == null) throw new IllegalArgumentException("Email must be saved before threading");
        String normalizedMessageId = normalizeMessageId(messageId);
        if (normalizedMessageId != null) email.setMessageId(normalizedMessageId);

        if (replyToEmailId == null) {
            email.setThreadRootId(email.getId());
        } else {
            ReplyContext context = replyContext(replyToEmailId);
            email.setThreadRootId(context.threadRootId());
            email.setInReplyTo(context.inReplyTo());
            email.setReferencesHeader(context.referencesHeader());
        }

        if (normalizedMessageId != null) {
            email = emails.save(email);
            reconcileAfterMessageId(email);
        }
    }

    /** Makes a just-persisted inbound root point to itself. */
    public void ensureThreadRoot(Email email) {
        if (email.getId() == null) throw new IllegalArgumentException("Email must be saved before threading");
        if (email.getThreadRootId() == null) email.setThreadRootId(email.getId());
    }

    /**
     * Repairs an inbound reply that arrived before an outbound email's delayed
     * Resend Message-ID webhook. The child initially formed its own local root;
     * relink that entire local branch when its parent becomes identifiable.
     */
    @Transactional
    public void reconcileAfterMessageId(Email parent) {
        if (parent.getId() == null || !"out".equals(parent.getDirection())
                || normalizeMessageId(parent.getMessageId()) == null) return;
        Long parentRootId = rootId(parent);
        if (parentRootId == null) return;

        List<Email> copies = emails.findByMessageIdAndDirectionAndCopyOfEmailIdIsNull(parent.getMessageId(), "in");
        for (Email copy : copies) {
            copy.setCopyOfEmailId(parent.getId());
            copy.setThreadRootId(parentRootId);
        }
        if (!copies.isEmpty()) emails.saveAll(copies);

        List<Email> relinked = new ArrayList<>();
        for (Email child : emails.findByInReplyToAndCopyOfEmailIdIsNull(parent.getMessageId())) {
            Long childRootId = rootId(child);
            if (childRootId == null || childRootId.equals(parentRootId)) continue;
            List<Email> branch = emails.findConversation(childRootId);
            mergeMailboxStates(childRootId, parentRootId, branch, emails.findConversation(parentRootId));
            for (Email message : branch) {
                message.setThreadRootId(parentRootId);
                relinked.add(message);
            }
        }
        if (!relinked.isEmpty()) emails.saveAll(relinked);
    }

    private void mergeMailboxStates(Long sourceRoot, Long targetRoot, List<Email> sourceMessages,
                                    List<Email> targetMessages) {
        var statesByUser = mailboxStates.findByThreadRootIdIn(List.of(sourceRoot, targetRoot)).stream()
                .collect(java.util.stream.Collectors.groupingBy(state -> state.getUser().getId()));
        for (var userStates : statesByUser.values()) {
            EmailMailboxState source = userStates.stream().filter(state -> sourceRoot.equals(state.getThreadRootId()))
                    .findFirst().orElse(null);
            EmailMailboxState target = userStates.stream().filter(state -> targetRoot.equals(state.getThreadRootId()))
                    .findFirst().orElse(null);
            boolean unread = hasUnread(sourceMessages, source) || hasUnread(targetMessages, target);
            Long lastRead = max(source == null ? null : source.getLastReadEmailId(),
                    target == null ? null : target.getLastReadEmailId());
            Long archivedThrough = archivedOrNoInbound(sourceMessages, source) && archivedOrNoInbound(targetMessages, target)
                    ? max(source == null ? null : source.getArchivedThroughEmailId(),
                          target == null ? null : target.getArchivedThroughEmailId()) : null;
            boolean starred = (source != null && source.isStarred()) || (target != null && target.isStarred());
            EmailMailboxState merged = target == null ? source : target;
            merged.setThreadRootId(targetRoot);
            merged.setLastReadEmailId(lastRead);
            merged.setMarkedUnread(unread);
            merged.setArchivedThroughEmailId(archivedThrough);
            merged.setStarred(starred);
            mailboxStates.save(merged);
            if (source != null && target != null) mailboxStates.delete(source);
        }
    }

    private static boolean hasUnread(List<Email> messages, EmailMailboxState state) {
        return (state != null && state.isMarkedUnread()) || messages.stream()
                .filter(message -> "in".equals(message.getDirection()))
                .anyMatch(message -> state == null || state.getLastReadEmailId() == null
                        || message.getId() > state.getLastReadEmailId());
    }

    private static boolean archivedOrNoInbound(List<Email> messages, EmailMailboxState state) {
        return messages.stream().filter(message -> "in".equals(message.getDirection()))
                .allMatch(message -> state != null && state.getArchivedThroughEmailId() != null
                        && message.getId() <= state.getArchivedThroughEmailId());
    }

    private static Long max(Long first, Long second) {
        return first == null ? second : second == null ? first : Math.max(first, second);
    }

    /** Returns every local message in the conversation in the order it was recorded. */
    public List<Email> conversation(Email email) {
        Long rootId = rootId(email);
        List<Email> conversation = emails.findConversation(rootId);
        return conversation.isEmpty() ? List.of(email) : conversation;
    }

    private java.util.Optional<Long> resolveThreadRoot(Email email) {
        List<String> candidates = new ArrayList<>();
        String inReplyTo = normalizeMessageId(email.getInReplyTo());
        if (inReplyTo != null) {
            var existing = emails.findFirstByMessageIdAndCopyOfEmailIdIsNullOrderByIdAsc(inReplyTo);
            if (existing.isPresent()) return java.util.Optional.of(rootId(existing.get()));
        }
        candidates.addAll(messageIds(email.getReferencesHeader()));
        for (int i = candidates.size() - 1; i >= 0; i--) {
            var existing = emails.findFirstByMessageIdAndCopyOfEmailIdIsNullOrderByIdAsc(candidates.get(i));
            if (existing.isPresent()) return java.util.Optional.of(rootId(existing.get()));
        }
        return java.util.Optional.empty();
    }

    private static Long rootId(Email email) {
        return email.getThreadRootId() != null ? email.getThreadRootId() : email.getId();
    }

    public static String replySubject(String subject) {
        String value = subject == null ? "" : subject.trim();
        value = value.replaceFirst("(?i)^(?:re:\\s*)+", "");
        return value.isBlank() ? "Re:" : "Re: " + value;
    }

    public static String normalizeMessageId(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return null;
        if (trimmed.length() > 998 || trimmed.chars().anyMatch(Character::isWhitespace)) return null;
        return trimmed;
    }

    public static String normalizeReferences(String value) {
        List<String> ids = messageIds(value);
        return ids.isEmpty() ? null : String.join(" ", ids);
    }

    private static String appendReference(String existing, String messageId) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(messageIds(existing));
        ids.add(messageId);
        List<String> trimmed = new ArrayList<>(ids);
        while (String.join(" ", trimmed).length() > 998 && trimmed.size() > 2) {
            // Preserve the root (first) and the direct parent (last), dropping
            // the oldest intermediate reference first.
            trimmed.remove(1);
        }
        String value = String.join(" ", trimmed);
        if (value.length() > 998) throw new IllegalStateException("Email Message-ID is too long to reply safely.");
        return value;
    }

    private static String replyRecipient(Email parent) {
        return "in".equals(parent.getDirection()) ? parent.getFromAddress() : parent.getToAddress();
    }

    private static String replySenderHint(Email parent) {
        String address = "in".equals(parent.getDirection()) ? parent.getToAddress() : parent.getFromAddress();
        return address == null ? null : address.split(",", 2)[0];
    }

    private static String normalizeMailbox(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        int start = trimmed.lastIndexOf('<');
        int end = trimmed.lastIndexOf('>');
        if (start >= 0 && end > start) trimmed = trimmed.substring(start + 1, end).trim();
        return trimmed.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") ? trimmed : null;
    }

    private static String replyAddresses(String value, String recipient, String sender) {
        if (value == null || value.isBlank()) return null;
        LinkedHashSet<String> addresses = new LinkedHashSet<>();
        for (String candidate : value.split("[,;\\s]+")) {
            String address = normalizeMailbox(candidate);
            if (address != null
                    && !address.equalsIgnoreCase(recipient)
                    && (sender == null || !address.equalsIgnoreCase(sender))) {
                addresses.add(address);
            }
        }
        return addresses.isEmpty() ? null : String.join(", ", addresses);
    }

    private static List<String> messageIds(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.trim().split("\\s+"))
                .map(EmailThreadService::normalizeMessageId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    public record ReplyContext(Long replyToEmailId, Long threadRootId,
                               String inReplyTo, String referencesHeader,
                               String subject, String recipientAddress,
                               String senderAddressHint, String ccAddresses,
                               String bccAddresses) {
        public ReplyContext(Long replyToEmailId, Long threadRootId,
                            String inReplyTo, String referencesHeader,
                            String subject, String recipientAddress,
                            String senderAddressHint) {
            this(replyToEmailId, threadRootId, inReplyTo, referencesHeader, subject,
                    recipientAddress, senderAddressHint, null, null);
        }

        public boolean providerThreaded() {
            return inReplyTo != null;
        }
    }

}

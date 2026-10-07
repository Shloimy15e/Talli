package dev.dynamiq.talli.service;

import dev.dynamiq.talli.model.Email;
import dev.dynamiq.talli.model.EmailMailboxState;
import dev.dynamiq.talli.model.User;
import dev.dynamiq.talli.repository.EmailMailboxStateRepository;
import dev.dynamiq.talli.repository.EmailRepository;
import dev.dynamiq.talli.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MailboxService {

    private static final int PAGE_SIZE = 25;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final EmailRepository emails;
    private final EmailMailboxStateRepository states;
    private final UserRepository users;
    private final EmailThreadService threads;

    public MailboxService(EmailRepository emails,
                          EmailMailboxStateRepository states,
                          UserRepository users,
                          EmailThreadService threads) {
        this.emails = emails;
        this.states = states;
        this.users = users;
        this.threads = threads;
    }

    @Transactional(readOnly = true)
    public User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @Transactional(readOnly = true)
    public MailboxView mailbox(User user, String folder, String search, int page,
                               String flow, List<String> statuses, String mailboxAddress) {
        String normalizedAddress = normalizeAddress(mailboxAddress);
        String normalizedFolder = normalizeFolder(folder);
        String normalizedSearch = normalizeSearch(search);
        String normalizedFlow = normalizeFlow(flow);
        List<String> normalizedStatuses = normalizeStatuses(statuses);
        boolean statusesEmpty = normalizedStatuses.isEmpty();
        List<String> statusesForQuery = statusesEmpty ? List.of("__none__") : normalizedStatuses;

        Page<Email> emailPage = emails.findMailboxConversations(
                user.getId(), normalizedFolder, normalizedFlow, statusesForQuery, statusesEmpty,
                normalizedSearch, normalizedAddress, PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        List<Long> rootIds = emailPage.stream().map(MailboxService::rootId).distinct().toList();
        Map<Long, ThreadStats> statsByRoot = conversationStats(rootIds);
        Map<Long, EmailMailboxState> stateByRoot = statesFor(user, rootIds);

        Page<MailRow> mailRows = emailPage.map(email -> {
            Long rootId = rootId(email);
            ThreadStats stats = statsByRoot.getOrDefault(rootId, new ThreadStats(1, 0));
            EmailMailboxState state = stateByRoot.get(rootId);
            return new MailRow(
                    email.getId(), rootId, email.getSubject(), contact(email), preview(email),
                    stats.messageCount(), isUnread(stats.latestInboundId(), state),
                    state != null && state.isStarred(), email.getStatus(), email.getDirection(),
                    email.getCreatedAt());
        });

        EmailRepository.MailboxCountsProjection counts = emails.countMailboxFolders(
                user.getId(), normalizedFlow, statusesForQuery, statusesEmpty, normalizedSearch, normalizedAddress);
        Map<String, Long> folderCounts = new LinkedHashMap<>();
        folderCounts.put("all", counts.getAllCount());
        folderCounts.put("inbox", emails.countUnreadInboxConversations(user.getId(), normalizedAddress));
        folderCounts.put("sent", counts.getSentCount());
        folderCounts.put("starred", counts.getStarredCount());
        folderCounts.put("archive", counts.getArchiveCount());

        return new MailboxView(mailRows, folderCounts, normalizedFolder, normalizedSearch, normalizedAddress);
    }

    @Transactional
    public ConversationView conversation(User user, Long emailId, boolean markRead) {
        Email selected = findEmail(emailId);
        Long rootId = rootId(selected);
        List<Email> conversation = threads.conversation(selected);
        Email latest = latest(conversation);
        EmailMailboxState state = states.findByUserIdAndThreadRootId(user.getId(), rootId).orElse(null);

        if (markRead) {
            state = state == null ? newState(user, rootId) : state;
            state.setLastReadEmailId(latest.getId());
            state.setMarkedUnread(false);
            state = states.save(state);
        }

        long latestInboundId = conversation.stream()
                .filter(email -> "in".equals(email.getDirection()))
                .map(Email::getId)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        return new ConversationView(selected, List.copyOf(conversation), rootId,
                state != null && state.isStarred(), isArchived(latestInboundId, state));
    }

    @Transactional
    public void update(User user, Long emailId, String actionName) {
        Email selected = findEmail(emailId);
        Long rootId = rootId(selected);
        MailboxAction action = MailboxAction.from(actionName);
        EmailMailboxState state = states.findByUserIdAndThreadRootId(user.getId(), rootId).orElse(null);

        if (state == null && (action == MailboxAction.UNSTAR || action == MailboxAction.UNARCHIVE)) {
            return;
        }
        if (state == null) state = newState(user, rootId);

        Long latestEmailId = latest(threads.conversation(selected)).getId();
        switch (action) {
            case STAR -> state.setStarred(true);
            case UNSTAR -> state.setStarred(false);
            case ARCHIVE -> state.setArchivedThroughEmailId(latestEmailId);
            case UNARCHIVE -> state.setArchivedThroughEmailId(null);
            case READ -> {
                state.setLastReadEmailId(latestEmailId);
                state.setMarkedUnread(false);
            }
            case UNREAD -> state.setMarkedUnread(true);
        }
        states.save(state);
    }

    private Email findEmail(Long emailId) {
        Email selected = emails.findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found"));
        if (selected.getCopyOfEmailId() == null) return selected;
        return emails.findById(selected.getCopyOfEmailId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Canonical email not found"));
    }

    private Map<Long, ThreadStats> conversationStats(List<Long> rootIds) {
        if (rootIds.isEmpty()) return Map.of();
        return emails.summarizeConversations(rootIds).stream().collect(Collectors.toMap(
                row -> number(row[0]),
                row -> new ThreadStats(number(row[1]), number(row[2])),
                (first, ignored) -> first,
                LinkedHashMap::new));
    }

    private Map<Long, EmailMailboxState> statesFor(User user, List<Long> rootIds) {
        if (rootIds.isEmpty()) return Map.of();
        return states.findByUserIdAndThreadRootIdIn(user.getId(), rootIds).stream()
                .collect(Collectors.toMap(EmailMailboxState::getThreadRootId, Function.identity()));
    }

    private static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Email latest(List<Email> conversation) {
        return conversation.stream().max(Comparator
                        .comparing(Email::getCreatedAt)
                        .thenComparing(Email::getId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private static Long rootId(Email email) {
        return email.getThreadRootId() == null ? email.getId() : email.getThreadRootId();
    }

    private static EmailMailboxState newState(User user, Long rootId) {
        EmailMailboxState state = new EmailMailboxState();
        state.setUser(user);
        state.setThreadRootId(rootId);
        return state;
    }

    private static boolean isUnread(long latestInboundId, EmailMailboxState state) {
        return (state != null && state.isMarkedUnread())
                || (latestInboundId > 0
                && (state == null || state.getLastReadEmailId() == null
                    || latestInboundId > state.getLastReadEmailId()));
    }

    private static boolean isArchived(long latestInboundId, EmailMailboxState state) {
        return state != null && state.getArchivedThroughEmailId() != null
                && latestInboundId <= state.getArchivedThroughEmailId();
    }

    private static String contact(Email email) {
        if (email.getClient() != null && email.getClient().getName() != null
                && !email.getClient().getName().isBlank()) {
            return email.getClient().getName();
        }
        String address = "in".equals(email.getDirection()) ? email.getFromAddress() : email.getToAddress();
        return address == null ? "" : address;
    }

    private static String preview(Email email) {
        String value = email.getBody();
        if ((value == null || value.isBlank()) && email.getBodyHtml() != null) {
            value = HTML_TAG.matcher(email.getBodyHtml()).replaceAll(" ");
        }
        value = value == null ? "" : WHITESPACE.matcher(value).replaceAll(" ").trim();
        return value.length() <= 160 ? value : value.substring(0, 157) + "...";
    }

    public static String normalizeFolder(String folder) {
        if (folder == null) return "all";
        return switch (folder.toLowerCase(Locale.ROOT)) {
            case "all", "inbox", "sent", "starred", "archive" -> folder.toLowerCase(Locale.ROOT);
            default -> "all";
        };
    }

    public static String normalizeAddress(String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeSearch(String search) {
        return search == null ? "" : search.trim();
    }

    private static String normalizeFlow(String flow) {
        return "in".equals(flow) || "out".equals(flow) ? flow : "";
    }

    private static List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null) return List.of();
        return statuses.stream().filter(Objects::nonNull).map(String::trim)
                .filter(status -> !status.isEmpty()).distinct().toList();
    }

    public record MailboxView(Page<MailRow> mailRows, Map<String, Long> folderCounts,
                              String folder, String search, String mailboxAddress) {}

    public record MailRow(Long id, Long rootId, String subject, String contact, String preview,
                          long messageCount, boolean unread, boolean starred, String status,
                          String direction, LocalDateTime createdAt) {}

    public record ConversationView(Email selectedEmail, List<Email> conversation, Long rootId,
                                   boolean starred, boolean archived) {}

    private record ThreadStats(long messageCount, long latestInboundId) {}

    private enum MailboxAction {
        STAR, UNSTAR, ARCHIVE, UNARCHIVE, READ, UNREAD;

        static MailboxAction from(String value) {
            if (value == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mailbox action is required");
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown mailbox action");
            }
        }
    }
}

package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailRepository extends JpaRepository<Email, Long> {

    String MAILBOX_FROM_AND_FILTERS = """
             FROM Email e
             LEFT JOIN EmailMailboxState mailbox
               ON mailbox.user.id = :userId
              AND mailbox.threadRootId = COALESCE(e.threadRootId, e.id)
             WHERE e.copyOfEmailId IS NULL
               AND NOT EXISTS (
                 SELECT newer.id FROM Email newer
                 WHERE COALESCE(newer.threadRootId, newer.id) = COALESCE(e.threadRootId, e.id)
                   AND newer.copyOfEmailId IS NULL
                   AND (newer.createdAt > e.createdAt
                        OR (newer.createdAt = e.createdAt AND newer.id > e.id))
             )
               AND (:mailboxAddress = '' OR EXISTS (
                   SELECT addressEmail.id FROM Email addressEmail
                   WHERE COALESCE(addressEmail.threadRootId, addressEmail.id) = COALESCE(e.threadRootId, e.id)
                     AND addressEmail.copyOfEmailId IS NULL
                     AND (:mailboxAddress = ''
                          OR (addressEmail.direction = 'out' AND LOWER(addressEmail.fromAddress) = :mailboxAddress)
                          OR (addressEmail.direction = 'in' AND (LOCATE(CONCAT(',', :mailboxAddress, ','), CONCAT(',', REPLACE(LOWER(COALESCE(addressEmail.toAddress, '')), ' ', ''), ',')) > 0
                              OR LOCATE(CONCAT(',', :mailboxAddress, ','), CONCAT(',', REPLACE(LOWER(COALESCE(addressEmail.cc, '')), ' ', ''), ',')) > 0
                              OR LOCATE(CONCAT(',', :mailboxAddress, ','), CONCAT(',', REPLACE(LOWER(COALESCE(addressEmail.bcc, '')), ' ', ''), ',')) > 0)))
               ))
               AND (:flow = '' OR EXISTS (
                   SELECT flowEmail.id FROM Email flowEmail
                   WHERE COALESCE(flowEmail.threadRootId, flowEmail.id) = COALESCE(e.threadRootId, e.id)
                     AND flowEmail.copyOfEmailId IS NULL
                     AND flowEmail.direction = :flow
               ))
               AND (:statusesEmpty = true OR EXISTS (
                   SELECT statusEmail.id FROM Email statusEmail
                   WHERE COALESCE(statusEmail.threadRootId, statusEmail.id) = COALESCE(e.threadRootId, e.id)
                     AND statusEmail.copyOfEmailId IS NULL
                     AND statusEmail.status IN :statuses
               ))
               AND (:search = '' OR EXISTS (
                   SELECT searchEmail.id FROM Email searchEmail LEFT JOIN searchEmail.client searchClient
                   WHERE COALESCE(searchEmail.threadRootId, searchEmail.id) = COALESCE(e.threadRootId, e.id)
                     AND searchEmail.copyOfEmailId IS NULL
                     AND (LOWER(searchEmail.subject) LIKE LOWER(CONCAT('%', :search, '%'))
                          OR LOWER(searchEmail.toAddress) LIKE LOWER(CONCAT('%', :search, '%'))
                          OR LOWER(COALESCE(searchEmail.fromAddress, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                          OR LOWER(searchEmail.body) LIKE LOWER(CONCAT('%', :search, '%'))
                          OR LOWER(COALESCE(searchClient.name, '')) LIKE LOWER(CONCAT('%', :search, '%')))
               ))
               AND (
                   :folder = 'all'
                   OR (:folder = 'inbox'
                       AND EXISTS (
                           SELECT inboxEmail.id FROM Email inboxEmail
                           WHERE COALESCE(inboxEmail.threadRootId, inboxEmail.id) = COALESCE(e.threadRootId, e.id)
                             AND inboxEmail.copyOfEmailId IS NULL
                             AND inboxEmail.direction = 'in'
                             AND (:mailboxAddress = ''
                                  OR LOCATE(CONCAT(',', :mailboxAddress, ','), CONCAT(',', REPLACE(LOWER(COALESCE(inboxEmail.toAddress, '')), ' ', ''), ',')) > 0
                                  OR LOCATE(CONCAT(',', :mailboxAddress, ','), CONCAT(',', REPLACE(LOWER(COALESCE(inboxEmail.cc, '')), ' ', ''), ',')) > 0
                                  OR LOCATE(CONCAT(',', :mailboxAddress, ','), CONCAT(',', REPLACE(LOWER(COALESCE(inboxEmail.bcc, '')), ' ', ''), ',')) > 0)
                       )
                       AND (mailbox.archivedThroughEmailId IS NULL OR EXISTS (
                           SELECT newInboxEmail.id FROM Email newInboxEmail
                           WHERE COALESCE(newInboxEmail.threadRootId, newInboxEmail.id) = COALESCE(e.threadRootId, e.id)
                             AND newInboxEmail.copyOfEmailId IS NULL
                             AND newInboxEmail.direction = 'in'
                             AND newInboxEmail.id > mailbox.archivedThroughEmailId
                       )))
                   OR (:folder = 'sent' AND EXISTS (
                       SELECT sentEmail.id FROM Email sentEmail
                       WHERE COALESCE(sentEmail.threadRootId, sentEmail.id) = COALESCE(e.threadRootId, e.id)
                         AND sentEmail.copyOfEmailId IS NULL
                         AND sentEmail.direction = 'out'
                         AND (:mailboxAddress = '' OR LOWER(sentEmail.fromAddress) = :mailboxAddress)
                   ))
                   OR (:folder = 'starred' AND mailbox.starred = true)
                   OR (:folder = 'archive'
                       AND mailbox.archivedThroughEmailId IS NOT NULL
                       AND NOT EXISTS (
                           SELECT unarchivedEmail.id FROM Email unarchivedEmail
                           WHERE COALESCE(unarchivedEmail.threadRootId, unarchivedEmail.id) = COALESCE(e.threadRootId, e.id)
                             AND unarchivedEmail.copyOfEmailId IS NULL
                             AND unarchivedEmail.direction = 'in'
                             AND unarchivedEmail.id > mailbox.archivedThroughEmailId
                       ))
               )
             """;
    List<Email> findAllByOrderByCreatedAtDesc();

    List<Email> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    Optional<Email> findByResendId(String resendId);

    Optional<Email> findFirstByMessageIdAndCopyOfEmailIdIsNullOrderByIdAsc(String messageId);

    Optional<Email> findFirstByMessageIdAndDirectionOrderByIdAsc(String messageId, String direction);

    List<Email> findByMessageIdAndDirectionAndCopyOfEmailIdIsNull(String messageId, String direction);

    List<Email> findByInReplyToAndCopyOfEmailIdIsNull(String inReplyTo);

    @Query("""
            SELECT e FROM Email e
            WHERE (e.id = :rootId OR e.threadRootId = :rootId)
              AND e.copyOfEmailId IS NULL
            ORDER BY e.createdAt ASC, e.id ASC
            """)
    List<Email> findConversation(@Param("rootId") Long rootId);

    @Query(value = "SELECT * FROM emails WHERE client_id = :clientId AND copy_of_email_id IS NULL ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Email> findClientEmails(@Param("clientId") Long clientId, @Param("limit") int limit,
                                 @Param("offset") long offset);

    @Query(value = "SELECT e " + MAILBOX_FROM_AND_FILTERS + " ORDER BY e.createdAt DESC, e.id DESC",
            countQuery = "SELECT COUNT(e) " + MAILBOX_FROM_AND_FILTERS)
    Page<Email> findMailboxConversations(@Param("userId") Long userId,
                                         @Param("folder") String folder,
                                         @Param("flow") String flow,
                                         @Param("statuses") List<String> statuses,
                                         @Param("statusesEmpty") boolean statusesEmpty,
                                         @Param("search") String search,
                                         @Param("mailboxAddress") String mailboxAddress,
                                         Pageable pageable);

    @Query("""
            SELECT COALESCE(e.threadRootId, e.id), COUNT(e),
                   MAX(CASE WHEN e.direction = 'in' THEN e.id ELSE 0 END)
            FROM Email e
            WHERE COALESCE(e.threadRootId, e.id) IN :rootIds
              AND e.copyOfEmailId IS NULL
            GROUP BY COALESCE(e.threadRootId, e.id)
            """)
    List<Object[]> summarizeConversations(@Param("rootIds") List<Long> rootIds);

    @Query(value = """
            WITH conversation_counts AS (
                SELECT COALESCE(email.thread_root_id, email.id) AS root_id,
                       MAX(CASE WHEN email.direction = 'in' THEN email.id ELSE 0 END) AS latest_inbound_id,
                       MAX(CASE WHEN email.direction = 'in' AND (:mailboxAddress = '' OR (
                           POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(email.to_address, '')), ' ', ''), ',')) > 0
                           OR POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(email.cc, '')), ' ', ''), ',')) > 0
                           OR POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(email.bcc, '')), ' ', ''), ',')) > 0)) THEN 1 ELSE 0 END) AS has_inbound,
                       MAX(CASE WHEN email.direction = 'out' AND (:mailboxAddress = '' OR LOWER(email.from_address) = :mailboxAddress) THEN 1 ELSE 0 END) AS has_outbound
                FROM emails email
                WHERE email.copy_of_email_id IS NULL
                  AND (:mailboxAddress = '' OR EXISTS (
                    SELECT 1 FROM emails address_email
                    WHERE COALESCE(address_email.thread_root_id, address_email.id) = COALESCE(email.thread_root_id, email.id)
                      AND address_email.copy_of_email_id IS NULL
                      AND (:mailboxAddress = ''
                       OR (address_email.direction = 'out' AND LOWER(address_email.from_address) = :mailboxAddress)
                       OR (address_email.direction = 'in' AND (POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(address_email.to_address, '')), ' ', ''), ',')) > 0
                           OR POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(address_email.cc, '')), ' ', ''), ',')) > 0
                           OR POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(address_email.bcc, '')), ' ', ''), ',')) > 0)))
                  ))
                  AND (:flow = '' OR EXISTS (
                    SELECT 1 FROM emails flow_email
                    WHERE COALESCE(flow_email.thread_root_id, flow_email.id) = COALESCE(email.thread_root_id, email.id)
                      AND flow_email.copy_of_email_id IS NULL
                      AND flow_email.direction = :flow
                ))
                  AND (:statusesEmpty = true OR EXISTS (
                    SELECT 1 FROM emails status_email
                    WHERE COALESCE(status_email.thread_root_id, status_email.id) = COALESCE(email.thread_root_id, email.id)
                      AND status_email.copy_of_email_id IS NULL
                      AND status_email.status IN (:statuses)
                ))
                  AND (:search = '' OR EXISTS (
                    SELECT 1 FROM emails search_email
                    LEFT JOIN clients search_client ON search_client.id = search_email.client_id
                    WHERE COALESCE(search_email.thread_root_id, search_email.id) = COALESCE(email.thread_root_id, email.id)
                      AND search_email.copy_of_email_id IS NULL
                      AND (LOWER(search_email.subject) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(search_email.to_address) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(search_email.from_address, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(search_email.body) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(COALESCE(search_client.name, '')) LIKE LOWER(CONCAT('%', :search, '%')))
                ))
                GROUP BY COALESCE(email.thread_root_id, email.id)
            )
            SELECT COUNT(*) AS "allCount",
                   COALESCE(SUM(CASE
                       WHEN counts.has_inbound = 1
                        AND (mailbox.archived_through_email_id IS NULL
                             OR counts.latest_inbound_id > mailbox.archived_through_email_id)
                       THEN 1 ELSE 0 END), 0) AS "inboxCount",
                   COALESCE(SUM(CASE WHEN counts.has_outbound = 1 THEN 1 ELSE 0 END), 0) AS "sentCount",
                   COALESCE(SUM(CASE WHEN mailbox.starred = true THEN 1 ELSE 0 END), 0) AS "starredCount",
                   COALESCE(SUM(CASE
                       WHEN mailbox.archived_through_email_id IS NOT NULL
                        AND counts.latest_inbound_id <= mailbox.archived_through_email_id
                       THEN 1 ELSE 0 END), 0) AS "archiveCount"
            FROM conversation_counts counts
            LEFT JOIN email_mailbox_states mailbox
              ON mailbox.user_id = :userId
             AND mailbox.thread_root_id = counts.root_id
            """, nativeQuery = true)
    MailboxCountsProjection countMailboxFolders(@Param("userId") Long userId,
                                                @Param("flow") String flow,
                                                @Param("statuses") List<String> statuses,
                                                @Param("statusesEmpty") boolean statusesEmpty,
                                                @Param("search") String search,
                                                @Param("mailboxAddress") String mailboxAddress);

    @Query(value = """
            WITH inbox_conversations AS (
                SELECT COALESCE(email.thread_root_id, email.id) AS root_id,
                       MAX(CASE WHEN email.direction = 'in' THEN email.id ELSE NULL END) AS latest_inbound_id
                FROM emails email
                WHERE email.copy_of_email_id IS NULL
                  AND (:mailboxAddress = '' OR EXISTS (
                    SELECT 1 FROM emails inbox_email
                    WHERE COALESCE(inbox_email.thread_root_id, inbox_email.id) = COALESCE(email.thread_root_id, email.id)
                      AND inbox_email.copy_of_email_id IS NULL
                      AND inbox_email.direction = 'in'
                      AND (POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(inbox_email.to_address, '')), ' ', ''), ',')) > 0
                           OR POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(inbox_email.cc, '')), ' ', ''), ',')) > 0
                           OR POSITION(CONCAT(',', :mailboxAddress, ',') IN CONCAT(',', REPLACE(LOWER(COALESCE(inbox_email.bcc, '')), ' ', ''), ',')) > 0)
                  ))
                GROUP BY COALESCE(email.thread_root_id, email.id)
            )
            SELECT COUNT(*)
            FROM inbox_conversations conversations
            LEFT JOIN email_mailbox_states mailbox
              ON mailbox.user_id = :userId
             AND mailbox.thread_root_id = conversations.root_id
            WHERE conversations.latest_inbound_id IS NOT NULL
              AND (mailbox.archived_through_email_id IS NULL
                   OR conversations.latest_inbound_id > mailbox.archived_through_email_id)
              AND (mailbox.marked_unread = true
                   OR mailbox.last_read_email_id IS NULL
                   OR conversations.latest_inbound_id > mailbox.last_read_email_id)
            """, nativeQuery = true)
    long countUnreadInboxConversations(@Param("userId") Long userId,
                                       @Param("mailboxAddress") String mailboxAddress);

    default long countUnreadInboxConversations(Long userId) {
        return countUnreadInboxConversations(userId, "");
    }

    interface MailboxCountsProjection {
        long getAllCount();
        long getInboxCount();
        long getSentCount();
        long getStarredCount();
        long getArchiveCount();
    }

}

package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByIncurredOnDesc();

    Page<Expense> findAllByOrderByIncurredOnDesc(Pageable pageable);

    @Query(value = """
            SELECT e FROM Expense e
            LEFT JOIN FETCH e.client c
            LEFT JOIN FETCH e.project p
            LEFT JOIN FETCH e.subscription s
            LEFT JOIN FETCH e.invoice i
            WHERE (:search = ''
                OR LOWER(COALESCE(e.vendor, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.paymentMethod, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.mercuryTransactionId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(e.category) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(p.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(s.vendor, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(i.reference, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:clientId IS NULL OR c.id = :clientId)
              AND (:projectId IS NULL OR p.id = :projectId)
              AND (:category = '' OR e.category = :category)
              AND (:source = ''
                OR (:source = 'subscription' AND s IS NOT NULL)
                OR (:source = 'mercury' AND e.mercuryTransactionId IS NOT NULL)
                OR (:source = 'manual' AND s IS NULL AND e.mercuryTransactionId IS NULL))
              AND (:billing = ''
                OR (:billing = 'unbilled' AND e.billable = true AND e.billed = false)
                OR (:billing = 'billed' AND e.billed = true)
                OR (:billing = 'nonbillable' AND e.billable = false))
              AND (:fromDate IS NULL OR e.incurredOn >= :fromDate)
              AND (:toDate IS NULL OR e.incurredOn <= :toDate)
            ORDER BY e.incurredOn DESC, e.id DESC
            """,
            countQuery = """
            SELECT COUNT(e) FROM Expense e
            LEFT JOIN e.client c
            LEFT JOIN e.project p
            LEFT JOIN e.subscription s
            LEFT JOIN e.invoice i
            WHERE (:search = ''
                OR LOWER(COALESCE(e.vendor, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.paymentMethod, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.mercuryTransactionId, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(e.category) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(p.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(s.vendor, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(i.reference, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:clientId IS NULL OR c.id = :clientId)
              AND (:projectId IS NULL OR p.id = :projectId)
              AND (:category = '' OR e.category = :category)
              AND (:source = ''
                OR (:source = 'subscription' AND s IS NOT NULL)
                OR (:source = 'mercury' AND e.mercuryTransactionId IS NOT NULL)
                OR (:source = 'manual' AND s IS NULL AND e.mercuryTransactionId IS NULL))
              AND (:billing = ''
                OR (:billing = 'unbilled' AND e.billable = true AND e.billed = false)
                OR (:billing = 'billed' AND e.billed = true)
                OR (:billing = 'nonbillable' AND e.billable = false))
              AND (:fromDate IS NULL OR e.incurredOn >= :fromDate)
              AND (:toDate IS NULL OR e.incurredOn <= :toDate)
            """)
    Page<Expense> findFiltered(@Param("search") String search,
                               @Param("clientId") Long clientId,
                               @Param("projectId") Long projectId,
                               @Param("category") String category,
                               @Param("source") String source,
                               @Param("billing") String billing,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               Pageable pageable);

    List<Expense> findByIncurredOnBetweenOrderByIncurredOnDesc(LocalDate from, LocalDate to);

    List<Expense> findByClientIdOrderByIncurredOnDesc(Long clientId);

    List<Expense> findByClientIdAndBillableTrueAndBilledFalseAndIncurredOnBetweenOrderByIncurredOnAsc(
            Long clientId, LocalDate from, LocalDate to);

    List<Expense> findByInvoiceId(Long invoiceId);

    List<Expense> findByProjectIdOrderByIncurredOnDesc(Long projectId);

    List<Expense> findBySubscriptionIdOrderByIncurredOnDesc(Long subscriptionId);

    @Modifying(clearAutomatically = true)
    @Query("update Expense e set e.subscription = null where e.subscription.id = :subscriptionId")
    int unlinkAllFromSubscription(@Param("subscriptionId") Long subscriptionId);

    boolean existsByMercuryTransactionId(String mercuryTransactionId);

    // For the dashboard "this month" tile — sum of all expenses in the range.
    // Returns null if there are no rows, so callers must coalesce.
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM Expense e
            WHERE e.incurredOn BETWEEN :from AND :to
            """)
    BigDecimal sumAmountBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}

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

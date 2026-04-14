package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findAllByOrderByIncurredOnDesc();

    List<Expense> findByIncurredOnBetweenOrderByIncurredOnDesc(LocalDate from, LocalDate to);

    List<Expense> findByClientIdOrderByIncurredOnDesc(Long clientId);

    List<Expense> findByProjectIdOrderByIncurredOnDesc(Long projectId);

    List<Expense> findBySubscriptionIdOrderByIncurredOnDesc(Long subscriptionId);

    // For the dashboard "this month" tile — sum of all expenses in the range.
    // Returns null if there are no rows, so callers must coalesce.
    @org.springframework.data.jpa.repository.Query("""
            SELECT COALESCE(SUM(e.amount), 0)
            FROM Expense e
            WHERE e.incurredOn BETWEEN :from AND :to
            """)
    BigDecimal sumAmountBetween(
            @org.springframework.data.repository.query.Param("from") LocalDate from,
            @org.springframework.data.repository.query.Param("to") LocalDate to);
}

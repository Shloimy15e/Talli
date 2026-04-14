package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findAllByOrderByVendorAsc();

    List<Subscription> findByCancelledOnIsNullOrderByVendorAsc();

    List<Subscription> findByCancelledOnIsNotNullOrderByCancelledOnDesc();

    List<Subscription> findByCancelledOnIsNullAndNextDueOnLessThanEqualOrderByNextDueOnAsc(LocalDate cutoff);

    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN s.cycle = 'yearly' THEN s.amount / 12 ELSE s.amount END
            ), 0)
            FROM Subscription s
            WHERE s.cancelledOn IS NULL
            """)
    BigDecimal sumActiveMonthlyEquivalent();
}

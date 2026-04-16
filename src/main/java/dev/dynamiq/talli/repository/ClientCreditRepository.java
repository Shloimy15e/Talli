package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.ClientCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ClientCreditRepository extends JpaRepository<ClientCredit, Long> {

    List<ClientCredit> findByClientIdOrderByReceivedAtDesc(Long clientId);

    /**
     * Remaining balance for one credit = amount - sum(applied payments).
     * Returns BigDecimal so callers can compare to applicationAmount without
     * null-gymnastics (ZERO when no applications).
     */
    @Query("SELECT c.amount - COALESCE(SUM(p.amount), 0) "
         + "FROM ClientCredit c LEFT JOIN Payment p ON p.credit.id = c.id "
         + "WHERE c.id = :creditId "
         + "GROUP BY c.id, c.amount")
    BigDecimal remainingBalance(@Param("creditId") Long creditId);

    /**
     * Total unapplied credit balance for a client, optionally filtered by currency.
     * Used on client detail + dashboard tile.
     */
    @Query("SELECT COALESCE(SUM(c.amount - COALESCE(" +
           "  (SELECT SUM(p.amount) FROM Payment p WHERE p.credit = c), 0" +
           ")), 0) " +
           "FROM ClientCredit c " +
           "WHERE c.client.id = :clientId " +
           "AND (:currency IS NULL OR c.currency = :currency)")
    BigDecimal totalAvailableForClient(@Param("clientId") Long clientId,
                                       @Param("currency") String currency);

    /** Global total across all clients, for the dashboard tile. */
    @Query("SELECT COALESCE(SUM(c.amount - COALESCE(" +
           "  (SELECT SUM(p.amount) FROM Payment p WHERE p.credit = c), 0" +
           ")), 0) " +
           "FROM ClientCredit c")
    BigDecimal totalHeldOverall();
}

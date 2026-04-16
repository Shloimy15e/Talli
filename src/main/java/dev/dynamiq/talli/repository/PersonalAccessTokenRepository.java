package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    /** Eagerly fetch user + roles + permissions so the filter can build authorities outside the session. */
    @Query("SELECT t FROM PersonalAccessToken t " +
           "JOIN FETCH t.user u " +
           "LEFT JOIN FETCH u.roles r " +
           "LEFT JOIN FETCH r.permissions " +
           "WHERE t.tokenHash = :tokenHash")
    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findByUserIdOrderByCreatedAtDesc(Long userId);
}

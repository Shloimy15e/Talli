package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {
    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);
    List<PersonalAccessToken> findByUserIdOrderByCreatedAtDesc(Long userId);
}

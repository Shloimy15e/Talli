package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByInviteToken(String inviteToken);
    List<User> findAllByOrderByCreatedAtDesc();
    List<User> findByClientIdAndEnabledTrue(Long clientId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.enabled = true")
    List<User> findEnabledByRoleName(@org.springframework.data.repository.query.Param("roleName") String roleName);
}

package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.EmailSenderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface EmailSenderProfileRepository extends JpaRepository<EmailSenderProfile, Long> {

    List<EmailSenderProfile> findAllByOrderByDefaultSenderDescNameAsc();

    List<EmailSenderProfile> findAllByActiveTrueOrderByDefaultSenderDescNameAsc();

    Optional<EmailSenderProfile> findFirstByDefaultSenderTrueAndActiveTrue();

    Optional<EmailSenderProfile> findByAddressIgnoreCaseAndActiveTrue(String address);

    boolean existsByAddressIgnoreCase(String address);

    boolean existsByAddressIgnoreCaseAndIdNot(String address, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select profile from EmailSenderProfile profile order by profile.id")
    List<EmailSenderProfile> lockAll();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailSenderProfile profile set profile.defaultSender = false where profile.defaultSender = true")
    void clearDefault();
}

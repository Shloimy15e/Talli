package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.EmailMailboxState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmailMailboxStateRepository extends JpaRepository<EmailMailboxState, Long> {

    Optional<EmailMailboxState> findByUserIdAndThreadRootId(Long userId, Long threadRootId);

    List<EmailMailboxState> findByUserIdAndThreadRootIdIn(Long userId, Collection<Long> threadRootIds);
}

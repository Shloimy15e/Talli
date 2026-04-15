package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(String ownerType, Long ownerId);

    List<Media> findByOwnerTypeAndOwnerIdAndCollectionNameOrderByCreatedAtDesc(
            String ownerType, Long ownerId, String collectionName);

    Optional<Media> findFirstByOwnerTypeAndOwnerIdAndCollectionNameOrderByCreatedAtDesc(
            String ownerType, Long ownerId, String collectionName);

    void deleteByOwnerTypeAndOwnerId(String ownerType, Long ownerId);
}

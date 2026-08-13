package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Client;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// That's it. This interface becomes a fully-implemented repository at runtime.
// Spring Data JPA reads the type parameters <Client, Long> and generates:
//   - findAll()
//   - findById(Long id)
//   - save(Client c)       -- insert or update
//   - deleteById(Long id)
//   - count()
//   - existsById(Long id)
// ...and about 15 more methods. You never implement them.
public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByNameIgnoreCase(String name);
    Optional<Client> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findByIdForMercurySync(@Param("id") Long id);
}

package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

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
}

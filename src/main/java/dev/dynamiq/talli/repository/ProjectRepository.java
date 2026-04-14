package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Spring parses this method name:
    //   "find all projects where project.client.id = ?"
    // and generates the SQL automatically. No implementation needed.
    List<Project> findByClientId(Long clientId);
}

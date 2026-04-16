package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Spring parses this method name:
    //   "find all projects where project.client.id = ?"
    // and generates the SQL automatically. No implementation needed.
    List<Project> findByClientId(Long clientId);

    List<Project> findByStatusAndRateType(String status, String rateType);

    /** Active projects ordered by most recent time entry (projects with no entries last). */
    @Query("SELECT p FROM Project p LEFT JOIN TimeEntry t ON t.project = p " +
           "WHERE p.status = 'active' " +
           "GROUP BY p " +
           "ORDER BY MAX(t.startedAt) DESC NULLS LAST")
    List<Project> findActiveOrderedByRecentActivity();
}

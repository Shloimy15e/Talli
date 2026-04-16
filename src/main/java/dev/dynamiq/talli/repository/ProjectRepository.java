package dev.dynamiq.talli.repository;

import dev.dynamiq.talli.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Spring parses this method name:
    //   "find all projects where project.client.id = ?"
    // and generates the SQL automatically. No implementation needed.
    List<Project> findByClientId(Long clientId);

    List<Project> findByStatusAndRateType(String status, String rateType);

    List<Project> findByStatusOrderByNameAsc(String status);

    /** Active projects ordered by most recent time entry (projects with no entries last). */
    @Query("SELECT p FROM Project p LEFT JOIN TimeEntry t ON t.project = p " +
           "WHERE p.status = 'active' " +
           "GROUP BY p " +
           "ORDER BY MAX(t.startedAt) DESC NULLS LAST")
    List<Project> findActiveOrderedByRecentActivity();

    /**
     * Filtered projects with active-first sorting. Status filter, client filter,
     * and name/client-name search. Active projects always sort before non-active.
     */
    @Query("SELECT p FROM Project p JOIN FETCH p.client c WHERE "
         + "(:#{#statuses == null || #statuses.isEmpty()} = true OR p.status IN :statuses) AND "
         + "(:#{#clientIds == null || #clientIds.isEmpty()} = true OR c.id IN :clientIds) AND "
         + "(:search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) "
         + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) "
         + "ORDER BY CASE WHEN p.status = 'active' THEN 0 ELSE 1 END, p.name ASC")
    Page<Project> findFiltered(@Param("statuses") List<String> statuses,
                               @Param("clientIds") List<Long> clientIds,
                               @Param("search") String search,
                               Pageable pageable);
}

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
     * Filtered projects with last-activity timestamp (max TimeEntry.startedAt per project).
     * Result rows are [Project, lastActivity]. Active-first sort, then requested column.
     */
    @Query("SELECT p, MAX(t.startedAt) FROM Project p JOIN p.client c "
         + "LEFT JOIN TimeEntry t ON t.project = p "
         + "WHERE (:#{#statuses == null || #statuses.isEmpty()} = true OR p.status IN :statuses) AND "
         + "(:#{#clientIds == null || #clientIds.isEmpty()} = true OR c.id IN :clientIds) AND "
         + "(:search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) "
         + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) "
         + "GROUP BY p, c "
         + "ORDER BY CASE WHEN p.status = 'active' THEN 0 ELSE 1 END, "
         + "CASE WHEN :sort = 'activity' AND :direction = 'desc' THEN MAX(t.startedAt) END DESC NULLS LAST, "
         + "CASE WHEN :sort = 'activity' AND :direction = 'asc' THEN MAX(t.startedAt) END ASC NULLS LAST, "
         + "CASE WHEN :sort = 'name' AND :direction = 'asc' THEN p.name END ASC, "
         + "CASE WHEN :sort = 'name' AND :direction = 'desc' THEN p.name END DESC, "
         + "CASE WHEN :sort = 'client' AND :direction = 'asc' THEN c.name END ASC, "
         + "CASE WHEN :sort = 'client' AND :direction = 'desc' THEN c.name END DESC, "
         + "CASE WHEN :sort = 'created' AND :direction = 'desc' THEN p.createdAt END DESC, "
         + "CASE WHEN :sort = 'created' AND :direction = 'asc' THEN p.createdAt END ASC")
    Page<Object[]> findFilteredWithActivity(@Param("statuses") List<String> statuses,
                                            @Param("clientIds") List<Long> clientIds,
                                            @Param("search") String search,
                                            @Param("sort") String sort,
                                            @Param("direction") String direction,
                                            Pageable pageable);
}

package dev.dynamiq.talli.model;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.DriverManager;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeEntryRateMigrationTest {

    @Test
    void migrationBackfillsEveryEntriesProjectRateAndRequiresFutureRates() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:time_rate_migration;MODE=PostgreSQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE projects (id BIGINT PRIMARY KEY, current_rate DECIMAL(10,2) NOT NULL)");
                statement.execute("CREATE TABLE time_entries (id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES projects(id), billed BOOLEAN, ended_at TIMESTAMP)");
                statement.execute("INSERT INTO projects VALUES (1, 120.50), (2, 175.00)");
                statement.execute("INSERT INTO time_entries VALUES (1, 1, FALSE, CURRENT_TIMESTAMP), (2, 2, TRUE, CURRENT_TIMESTAMP), (3, 1, FALSE, NULL)");
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V52__time_entry_rate_snapshot.sql"));

            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT rate FROM time_entries ORDER BY id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("rate")).isEqualByComparingTo("120.50");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("rate")).isEqualByComparingTo("175.00");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal("rate")).isEqualByComparingTo("120.50");
                assertThat(rows.next()).isFalse();
            }
            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("INSERT INTO time_entries (id, project_id) VALUES (4, 1)"))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        }
    }

    @Test
    void directPersistenceCapturesProjectRateAndPreservesAnExplicitSnapshot() {
        Project project = new Project();
        project.setCurrentRate(new BigDecimal("120.00"));
        TimeEntry imported = new TimeEntry();
        imported.setProject(project);

        imported.onCreate();
        project.setCurrentRate(new BigDecimal("180.00"));
        imported.onUpdate();

        assertThat(imported.getRate()).isEqualByComparingTo("120.00");

        TimeEntry explicit = new TimeEntry();
        explicit.setProject(project);
        explicit.setRate(new BigDecimal("100.00"));
        explicit.onCreate();
        assertThat(explicit.getRate()).isEqualByComparingTo("100.00");
    }
}

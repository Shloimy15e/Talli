package dev.dynamiq.talli.model;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientDefaultRateMigrationTest {

    @Test
    void migrationLeavesExistingClientsUnsetAndPersistsOptionalNonnegativeRates() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:client_default_rate;MODE=PostgreSQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE clients (id BIGINT PRIMARY KEY)");
                statement.execute("INSERT INTO clients VALUES (1)");
            }
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V53__client_default_hourly_rate.sql"));

            try (var statement = connection.createStatement()) {
                try (var row = statement.executeQuery("SELECT default_hourly_rate FROM clients WHERE id = 1")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getBigDecimal(1)).isNull();
                }
                statement.execute("INSERT INTO clients VALUES (2, 125.50), (3, 0), (4, NULL)");
                try (var row = statement.executeQuery("SELECT default_hourly_rate FROM clients WHERE id = 2")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getBigDecimal(1)).isEqualByComparingTo("125.50");
                }
                assertThatThrownBy(() -> statement.execute("INSERT INTO clients VALUES (5, -1)"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void modelAcceptsUnsetOrZeroRatesAndRejectsNegativeRates() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            Client client = new Client();
            assertThat(validator.validate(client)).isEmpty();
            client.setDefaultHourlyRate(BigDecimal.ZERO);
            assertThat(validator.validate(client)).isEmpty();
            client.setDefaultHourlyRate(new BigDecimal("-0.01"));
            assertThat(validator.validate(client)).singleElement()
                    .satisfies(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("defaultHourlyRate"));
        }
    }
}

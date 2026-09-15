package dev.dynamiq.talli.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmailThreadMigrationTest {

    @Test
    void validatesTheActualEmailMigrationsAgainstAnIsolatedPostgresCluster() throws Exception {
        var postgresBin = Path.of(System.getProperty("test.postgres.bin", "C:/Program Files/PostgreSQL/17/bin"));
        var script = Path.of("scripts", "test-email-migrations.ps1").toAbsolutePath();

        assumeTrue(System.getProperty("os.name").startsWith("Windows"),
                "The isolated PostgreSQL migration check requires Windows.");
        assumeTrue(Files.isRegularFile(postgresBin.resolve("initdb.exe"))
                        && Files.isRegularFile(postgresBin.resolve("pg_ctl.exe"))
                        && Files.isRegularFile(postgresBin.resolve("psql.exe")),
                () -> "PostgreSQL 17 binaries are unavailable at " + postgresBin);
        assumeTrue(Files.isRegularFile(script), () -> "Migration validation script is unavailable at " + script);

        var process = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                "-PostgresBin", postgresBin.toString())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();

        assertThat(exitCode).as("PostgreSQL migration validation output:\n%s", output).isZero();
        assertThat(output).contains("Email migration validation passed");
    }
}

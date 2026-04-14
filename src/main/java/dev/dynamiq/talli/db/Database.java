package dev.dynamiq.talli.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// This class manages the SQLite connection and schema.
// It's like your database/migrations/ folder + config/database.php combined.
//
// Java uses JDBC (Java Database Connectivity) to talk to databases.
// JDBC is to Java what PDO is to PHP — a unified API for any database.
// The SQLite driver we added in pom.xml is like installing the PDO SQLite extension.
public class Database {

    // `static` = shared across all instances (like a PHP static property).
    // There's only one database, so one connection is fine.
    // This pattern is called a "singleton" — you'll see it everywhere in Java.
    private static Connection connection;

    // The DB file lives next to the jar. Like `database/database.sqlite` in Laravel.
    private static final String DB_URL = "jdbc:sqlite:talli.db";

    // Returns the single shared connection. Creates it on first call.
    // `synchronized` = thread-safe. If two threads call this at the same time,
    // only one runs at a time. Swing has multiple threads, so this matters.
    // PHP doesn't have this problem because each request is a separate process.
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL);
        }
        return connection;
    }

    // Creates tables if they don't exist — like running `php artisan migrate`.
    // The difference: no migration files, no up/down, no rollback tracking.
    // For a small app this is fine. For bigger apps you'd use Flyway or Liquibase
    // (Java's equivalents of Laravel migrations).
    public static void initialize() {
        try (Statement stmt = getConnection().createStatement()) {
            // `try-with-resources` = the (...) part auto-closes the Statement when done.
            // It's like Python's `with open(...) as f:` — guarantees cleanup.
            // PHP doesn't have this; you'd use try/finally or just let the GC handle it.

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS clients (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    email TEXT,
                    rate DECIMAL(10,2) NOT NULL,
                    rate_type TEXT NOT NULL DEFAULT 'hourly',
                    notes TEXT,
                    created_at DATE NOT NULL
                )
            """);
            // The triple-quote """ is a text block (Java 15+). Like PHP's heredoc <<<SQL.

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rate_changes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    client_id INTEGER NOT NULL,
                    old_rate DECIMAL(10,2),
                    new_rate DECIMAL(10,2) NOT NULL,
                    effective_date DATE NOT NULL,
                    acknowledged INTEGER NOT NULL DEFAULT 0,
                    notes TEXT,
                    created_at DATE NOT NULL,
                    FOREIGN KEY (client_id) REFERENCES clients(id)
                )
            """);

        } catch (SQLException e) {
            // e.printStackTrace() dumps the full stack trace — like dd($e) in Laravel.
            // Not great for production, but fine while building.
            e.printStackTrace();
        }
    }
}

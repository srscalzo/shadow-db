package com.shadowdb;

import com.shadowdb.model.SchemaDiff;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "diff",
    mixinStandardHelpOptions = true,
    description = "Preview schema changes from the latest Flyway migration."
)
public class DiffCommand implements Callable<Integer> {

    private static final Path MIGRATIONS_DIR = Path.of("src", "main", "resources", "db", "migration");

    @Option(names = {"--db-url"},
            description = "JDBC base URL without database name (default: ${DEFAULT-VALUE})",
            defaultValue = "${SHADOWDB_DB_URL:-jdbc:mysql://localhost:3306}")
    private String dbUrl;

    @Option(names = {"--db-user"},
            description = "Database username (default: ${DEFAULT-VALUE})",
            defaultValue = "${SHADOWDB_DB_USER:-root}")
    private String dbUser;

    @Option(names = {"--db-password"},
            description = "Database password",
            defaultValue = "${SHADOWDB_DB_PASSWORD:-}")
    private String dbPassword;

    @Override
    public Integer call() {
        MigrationFileReader   reader  = new MigrationFileReader();
        ShadowDatabaseManager manager = null;
        Connection            admin   = null;

        try {
            List<MigrationFileReader.MigrationFile> all      = reader.readAll(MIGRATIONS_DIR);
            MigrationFileReader.MigrationFile       newMig   = reader.identifyNewMigration(all);
            List<MigrationFileReader.MigrationFile> existing = reader.existingMigrations(all, newMig);

            System.out.println("Detected new migration: " + newMig.filename());
            System.out.println("Existing migrations:    " + existing.size());
            System.out.println();
            System.out.println("Connecting to " + dbUrl + " ...");

            admin = DriverManager.getConnection(
                dbUrl + "/mysql" + ShadowDatabaseManager.JDBC_PARAMS, dbUser, dbPassword);
            System.out.println("Connected.");
            System.out.println();

            manager = new ShadowDatabaseManager(admin, dbUrl, dbUser, dbPassword);
            ShadowDatabaseManager.ShadowConnections shadows = manager.createShadowDatabases();
            String targetVersion = existing.isEmpty() ? null : existing.get(existing.size() - 1).version();

            manager.applyMigrations(shadows.currentDbName(), all, targetVersion);
            manager.applyMigrations(shadows.newDbName(),     all, null);

            SchemaDiff diff = new SchemaDiffer().diff(
                shadows.current(), shadows.currentDbName(),
                shadows.newDb(),   shadows.newDbName(),
                newMig.filename());

            return new ReportPrinter().printReport(diff);

        } catch (SQLException e) {
            System.err.println("ERROR: Cannot connect to database at " + dbUrl);
            System.err.println("       " + e.getMessage());
            System.err.println("       Ensure MySQL is running and --db-url, --db-user, --db-password are correct.");
            return 2;
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("ERROR: " + e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace(System.err);
            return 2;
        } finally {
            if (manager != null) manager.close();
            if (admin   != null) try { admin.close(); } catch (SQLException ignored) {}
        }
    }
}

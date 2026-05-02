package com.shadowdb;

import com.shadowdb.model.SchemaDiff;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndToEndTest {

    private static final String H2_PARAMS = ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    @Test
    void detectsNewTableAddedByLatestMigration() throws Exception {
        Path migrationsDir = testMigrationsPath();

        MigrationFileReader reader = new MigrationFileReader();
        List<MigrationFileReader.MigrationFile> all      = reader.readAll(migrationsDir);
        MigrationFileReader.MigrationFile       newMig   = reader.identifyNewMigration(all);
        List<MigrationFileReader.MigrationFile> existing = reader.existingMigrations(all, newMig);

        assertEquals("V2__add_users_table.sql", newMig.filename());
        assertEquals(1, existing.size());

        long   ts         = System.currentTimeMillis();
        String currentUrl = "jdbc:h2:mem:shadow_current_" + ts + H2_PARAMS;
        String newUrl     = "jdbc:h2:mem:shadow_new_"     + ts + H2_PARAMS;
        String target     = existing.get(existing.size() - 1).version();

        try (Connection currentConn = DriverManager.getConnection(currentUrl, "sa", "");
             Connection newConn     = DriverManager.getConnection(newUrl,     "sa", "")) {

            applyMigrations(currentUrl, all, target);
            applyMigrations(newUrl,     all, null);

            SchemaDiff diff = new SchemaDiffer().diff(
                currentConn, "public",
                newConn,     "public",
                newMig.filename());

            assertTrue(diff.hasChanges());
            assertTrue(diff.tablesAdded().contains("users"),
                "Expected 'users' in tablesAdded, got: " + diff.tablesAdded());
            assertFalse(diff.columnsAdded().isEmpty());
            assertTrue(diff.columnsAdded().stream().anyMatch(c -> c.tableName().equals("users")));
        }
    }

    @Test
    void emptyCurrentSchemaWhenNoExistingMigrations() throws Exception {
        Path migrationsDir = testMigrationsPath();

        MigrationFileReader reader = new MigrationFileReader();
        List<MigrationFileReader.MigrationFile> all    = reader.readAll(migrationsDir);
        MigrationFileReader.MigrationFile       newMig = all.get(0);

        long   ts         = System.currentTimeMillis();
        String currentUrl = "jdbc:h2:mem:shadow_current_empty_" + ts + H2_PARAMS;
        String newUrl     = "jdbc:h2:mem:shadow_new_empty_"     + ts + H2_PARAMS;

        try (Connection currentConn = DriverManager.getConnection(currentUrl, "sa", "");
             Connection newConn     = DriverManager.getConnection(newUrl,     "sa", "")) {

            // shadow_current gets nothing; shadow_new gets V1 only
            applyMigrations(newUrl, all, newMig.version());

            SchemaDiff diff = new SchemaDiffer().diff(
                currentConn, "public",
                newConn,     "public",
                newMig.filename());

            assertTrue(diff.hasChanges());
            assertTrue(diff.tablesAdded().contains("products"));
        }
    }

    private void applyMigrations(String url,
                                 List<MigrationFileReader.MigrationFile> files,
                                 String targetVersion) {
        if (files.isEmpty()) return;
        String migrationsPath = files.get(0).path().getParent().toAbsolutePath().toString();
        Flyway.configure()
            .dataSource(url, "sa", "")
            .schemas("public")
            .locations("filesystem:" + migrationsPath)
            .createSchemas(false)
            .mixed(true)
            .target(targetVersion != null
                ? MigrationVersion.fromVersion(targetVersion)
                : MigrationVersion.LATEST)
            .load()
            .migrate();
    }

    private Path testMigrationsPath() throws Exception {
        URL resource = getClass().getClassLoader().getResource("migrations");
        assertNotNull(resource, "Test migrations not found on classpath");
        return Path.of(resource.toURI());
    }
}

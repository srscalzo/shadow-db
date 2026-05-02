package com.shadowdb;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class ShadowDatabaseManager implements AutoCloseable {

    static final String JDBC_PARAMS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    public record ShadowConnections(
        Connection current, Connection newDb,
        String currentDbName, String newDbName
    ) {}

    private final Connection adminConnection;
    private final String baseJdbcUrl;
    private final String username;
    private final String password;
    private String shadowCurrentDb;
    private String shadowNewDb;

    public ShadowDatabaseManager(Connection adminConnection, String baseJdbcUrl,
                                 String username, String password) {
        this.adminConnection = adminConnection;
        this.baseJdbcUrl     = baseJdbcUrl;
        this.username        = username;
        this.password        = password;
    }

    public ShadowConnections createShadowDatabases() throws SQLException {
        long ts = System.currentTimeMillis();
        shadowCurrentDb = "shadowdb_current_" + ts;
        shadowNewDb     = "shadowdb_new_"     + ts;

        try (Statement stmt = adminConnection.createStatement()) {
            stmt.execute("CREATE DATABASE `" + shadowCurrentDb + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            stmt.execute("CREATE DATABASE `" + shadowNewDb     + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }

        Connection currentConn = DriverManager.getConnection(buildUrlForSchema(shadowCurrentDb), username, password);
        Connection newConn     = DriverManager.getConnection(buildUrlForSchema(shadowNewDb),     username, password);

        return new ShadowConnections(currentConn, newConn, shadowCurrentDb, shadowNewDb);
    }

    public void applyMigrations(String schemaName,
                                List<MigrationFileReader.MigrationFile> migrationFiles,
                                String targetVersion) {
        if (migrationFiles.isEmpty()) return;
        String migrationsPath = migrationFiles.get(0).path().getParent().toAbsolutePath().toString();

        Flyway.configure()
            .dataSource(buildUrlForSchema(schemaName), username, password)
            .schemas(schemaName)
            .locations("filesystem:" + migrationsPath)
            .createSchemas(false)
            .mixed(true)
            .target(targetVersion != null
                ? MigrationVersion.fromVersion(targetVersion)
                : MigrationVersion.LATEST)
            .load()
            .migrate();
    }

    public String buildUrlForSchema(String schemaName) {
        return baseJdbcUrl + "/" + schemaName + JDBC_PARAMS;
    }

    @Override
    public void close() {
        dropQuietly(shadowCurrentDb);
        dropQuietly(shadowNewDb);
    }

    private void dropQuietly(String dbName) {
        if (dbName == null) return;
        try (Statement stmt = adminConnection.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS `" + dbName + "`");
        } catch (SQLException ignored) {
        }
    }
}

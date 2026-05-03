package com.shadowdb;

import com.shadowdb.model.ColumnDiff;
import com.shadowdb.model.SchemaDiff;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaDifferTest {

    private static final String H2_PARAMS = ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    private Connection currentConn;
    private Connection newConn;

    @BeforeEach
    void setUp() throws SQLException {
        long ts = System.nanoTime();
        currentConn = DriverManager.getConnection("jdbc:h2:mem:differ_current_" + ts + H2_PARAMS, "sa", "");
        newConn     = DriverManager.getConnection("jdbc:h2:mem:differ_new_"     + ts + H2_PARAMS, "sa", "");
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (currentConn != null) currentConn.close();
        if (newConn     != null) newConn.close();
    }

    // ---- helpers ----

    private void exec(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private SchemaDiff diff() throws SQLException {
        return new SchemaDiffer().diff(currentConn, "public", newConn, "public", "V_test.sql");
    }

    // ---- no-change baseline ----

    @Test
    void noChangesWhenSchemasIdentical() throws Exception {
        String ddl = "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255) NOT NULL, PRIMARY KEY (id))";
        exec(currentConn, ddl);
        exec(newConn,     ddl);

        SchemaDiff diff = diff();
        assertFalse(diff.hasChanges());
        assertTrue(diff.tablesAdded().isEmpty());
        assertTrue(diff.tablesRemoved().isEmpty());
        assertTrue(diff.columnsAdded().isEmpty());
        assertTrue(diff.columnsRemoved().isEmpty());
        assertTrue(diff.columnsModified().isEmpty());
    }

    // ---- table-level changes ----

    @Test
    void detectsTableAdded() throws Exception {
        exec(newConn, "CREATE TABLE users (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertTrue(diff.tablesAdded().contains("users"));
        assertTrue(diff.tablesRemoved().isEmpty());
    }

    @Test
    void detectsTableRemoved() throws Exception {
        exec(currentConn, "CREATE TABLE users (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertTrue(diff.tablesRemoved().contains("users"));
        assertTrue(diff.tablesAdded().isEmpty());
    }

    // ---- column-level changes ----

    @Test
    void detectsColumnAdded() throws Exception {
        exec(currentConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");
        exec(newConn,     "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), price DECIMAL(10,2) NOT NULL, PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertTrue(diff.tablesAdded().isEmpty());
        assertEquals(1, diff.columnsAdded().size());
        ColumnDiff added = diff.columnsAdded().get(0);
        assertEquals("products", added.tableName());
        assertEquals("price",    added.columnName());
        assertEquals("NO",       added.isNullable());
    }

    @Test
    void detectsColumnRemoved() throws Exception {
        exec(currentConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), price DECIMAL(10,2), PRIMARY KEY (id))");
        exec(newConn,     "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertEquals(1, diff.columnsRemoved().size());
        assertEquals("price", diff.columnsRemoved().get(0).columnName());
    }

    @Test
    void detectsColumnNullabilityModified() throws Exception {
        exec(currentConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");
        exec(newConn,     "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255) NOT NULL, PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertEquals(1, diff.columnsModified().size());
        ColumnDiff mod = diff.columnsModified().get(0);
        assertEquals("name", mod.columnName());
        assertEquals("YES",  mod.previousNullable());
        assertEquals("NO",   mod.isNullable());
    }

    @Test
    void detectsColumnLengthModified() throws Exception {
        exec(currentConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(100), PRIMARY KEY (id))");
        exec(newConn,     "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertEquals(1, diff.columnsModified().size());
        ColumnDiff mod = diff.columnsModified().get(0);
        assertEquals("name", mod.columnName());
        assertEquals(255L,   mod.characterMaxLength());
    }

    @Test
    void detectsColumnDefaultAdded() throws Exception {
        exec(currentConn, "CREATE TABLE orders (id INT NOT NULL AUTO_INCREMENT, status VARCHAR(50), PRIMARY KEY (id))");
        exec(newConn,     "CREATE TABLE orders (id INT NOT NULL AUTO_INCREMENT, status VARCHAR(50) DEFAULT 'pending', PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertEquals(1, diff.columnsModified().size());
        assertEquals("status",    diff.columnsModified().get(0).columnName());
        assertEquals("'pending'", diff.columnsModified().get(0).columnDefault());
    }

    // ---- multiple simultaneous changes ----

    @Test
    void detectsMultipleChangesSimultaneously() throws Exception {
        exec(currentConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), PRIMARY KEY (id))");
        exec(currentConn, "CREATE TABLE orders   (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");

        exec(newConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, name VARCHAR(255), price DECIMAL(10,2), PRIMARY KEY (id))");
        exec(newConn, "CREATE TABLE users    (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertTrue(diff.tablesAdded().contains("users"));
        assertTrue(diff.tablesRemoved().contains("orders"));
        assertTrue(diff.columnsAdded().stream()
            .anyMatch(c -> c.tableName().equals("products") && c.columnName().equals("price")));
    }

    // ---- empty schema edge cases ----

    @Test
    void noChangesWhenBothSchemasEmpty() throws Exception {
        SchemaDiff diff = diff();
        assertFalse(diff.hasChanges());
    }

    @Test
    void detectsAllTablesAddedWhenCurrentIsEmpty() throws Exception {
        exec(newConn, "CREATE TABLE users    (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");
        exec(newConn, "CREATE TABLE products (id INT NOT NULL AUTO_INCREMENT, PRIMARY KEY (id))");

        SchemaDiff diff = diff();
        assertTrue(diff.hasChanges());
        assertEquals(2, diff.tablesAdded().size());
        assertTrue(diff.tablesAdded().contains("users"));
        assertTrue(diff.tablesAdded().contains("products"));
    }
}

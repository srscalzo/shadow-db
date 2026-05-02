package com.shadowdb;

import com.shadowdb.model.ColumnDiff;
import com.shadowdb.model.IndexDiff;
import com.shadowdb.model.SchemaDiff;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class SchemaDiffer {

    private static final String EXCLUDED_TABLE = "flyway_schema_history";

    public SchemaDiff diff(Connection currentConn, String currentSchema,
                           Connection newConn,    String newSchema,
                           String newMigrationFilename) throws SQLException {

        Map<String, TableInfo>         currentTables  = loadTables(currentConn, currentSchema);
        Map<String, TableInfo>         newTables      = loadTables(newConn, newSchema);
        Map<String, List<ColumnInfo>>  currentColumns = loadColumns(currentConn, currentSchema);
        Map<String, List<ColumnInfo>>  newColumns     = loadColumns(newConn, newSchema);
        Map<String, List<IndexInfo>>   currentIndexes = loadIndexes(currentConn, currentSchema);
        Map<String, List<IndexInfo>>   newIndexes     = loadIndexes(newConn, newSchema);

        List<String> tablesAdded   = new ArrayList<>();
        List<String> tablesRemoved = new ArrayList<>();
        List<ColumnDiff> columnsAdded    = new ArrayList<>();
        List<ColumnDiff> columnsRemoved  = new ArrayList<>();
        List<ColumnDiff> columnsModified = new ArrayList<>();
        List<IndexDiff>  indexesAdded    = new ArrayList<>();
        List<IndexDiff>  indexesRemoved  = new ArrayList<>();

        // Tables added / removed
        for (String table : newTables.keySet()) {
            if (!currentTables.containsKey(table)) tablesAdded.add(table);
        }
        for (String table : currentTables.keySet()) {
            if (!newTables.containsKey(table)) tablesRemoved.add(table);
        }

        // Columns: compare tables present in both
        Set<String> allTables = new LinkedHashSet<>();
        allTables.addAll(currentTables.keySet());
        allTables.addAll(newTables.keySet());

        for (String table : allTables) {
            Map<String, ColumnInfo> curCols = toMap(currentColumns.getOrDefault(table, List.of()));
            Map<String, ColumnInfo> nwCols  = toMap(newColumns.getOrDefault(table, List.of()));

            for (Map.Entry<String, ColumnInfo> entry : nwCols.entrySet()) {
                String     colName = entry.getKey();
                ColumnInfo nwCol   = entry.getValue();
                if (!curCols.containsKey(colName)) {
                    columnsAdded.add(toColumnDiff(table, nwCol, null));
                } else {
                    ColumnInfo curCol = curCols.get(colName);
                    if (columnChanged(curCol, nwCol)) {
                        columnsModified.add(toColumnDiff(table, nwCol, curCol));
                    }
                }
            }
            for (Map.Entry<String, ColumnInfo> entry : curCols.entrySet()) {
                if (!nwCols.containsKey(entry.getKey())) {
                    columnsRemoved.add(toColumnDiff(table, entry.getValue(), null));
                }
            }
        }

        // Indexes
        for (String table : allTables) {
            Map<String, IndexInfo> curIdx = toIndexMap(currentIndexes.getOrDefault(table, List.of()));
            Map<String, IndexInfo> nwIdx  = toIndexMap(newIndexes.getOrDefault(table, List.of()));

            for (Map.Entry<String, IndexInfo> entry : nwIdx.entrySet()) {
                if (!curIdx.containsKey(entry.getKey())) {
                    indexesAdded.add(toIndexDiff(table, entry.getValue()));
                }
            }
            for (Map.Entry<String, IndexInfo> entry : curIdx.entrySet()) {
                if (!nwIdx.containsKey(entry.getKey())) {
                    indexesRemoved.add(toIndexDiff(table, entry.getValue()));
                }
            }
        }

        return new SchemaDiff(tablesAdded, tablesRemoved,
            columnsAdded, columnsRemoved, columnsModified,
            indexesAdded, indexesRemoved,
            newMigrationFilename);
    }

    private Map<String, TableInfo> loadTables(Connection conn, String schema) throws SQLException {
        Map<String, TableInfo> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME != ?")) {
            ps.setString(1, schema);
            ps.setString(2, EXCLUDED_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    result.put(name, new TableInfo(name, rs.getString("TABLE_TYPE")));
                }
            }
        }
        return result;
    }

    private Map<String, List<ColumnInfo>> loadColumns(Connection conn, String schema) throws SQLException {
        Map<String, List<ColumnInfo>> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, " +
                "COLUMN_DEFAULT, CHARACTER_MAXIMUM_LENGTH, ORDINAL_POSITION " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME != ? " +
                "ORDER BY TABLE_NAME, ORDINAL_POSITION")) {
            ps.setString(1, schema);
            ps.setString(2, EXCLUDED_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    ColumnInfo col = new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("DATA_TYPE"),
                        rs.getString("IS_NULLABLE"),
                        rs.getString("COLUMN_DEFAULT"),
                        rs.getObject("CHARACTER_MAXIMUM_LENGTH") != null
                            ? rs.getLong("CHARACTER_MAXIMUM_LENGTH") : null,
                        rs.getInt("ORDINAL_POSITION")
                    );
                    result.computeIfAbsent(table, k -> new ArrayList<>()).add(col);
                }
            }
        }
        return result;
    }

    private Map<String, List<IndexInfo>> loadIndexes(Connection conn, String schema) throws SQLException {
        // information_schema.STATISTICS is MySQL-specific; skip for other databases (e.g. H2 in tests)
        String dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
        if (!dbProduct.contains("mysql") && !dbProduct.contains("mariadb")) {
            return Collections.emptyMap();
        }

        // Groups multi-column indexes by INDEX_NAME
        Map<String, Map<String, IndexInfo>> byTable = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE " +
                "FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME != ? " +
                "ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX")) {
            ps.setString(1, schema);
            ps.setString(2, EXCLUDED_TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String table     = rs.getString("TABLE_NAME");
                    String indexName = rs.getString("INDEX_NAME");
                    String colName   = rs.getString("COLUMN_NAME");
                    boolean nonUnique = rs.getBoolean("NON_UNIQUE");

                    byTable
                        .computeIfAbsent(table, k -> new LinkedHashMap<>())
                        .computeIfAbsent(indexName, k -> new IndexInfo(indexName, new ArrayList<>(), !nonUnique))
                        .columns().add(colName);
                }
            }
        }

        Map<String, List<IndexInfo>> result = new LinkedHashMap<>();
        byTable.forEach((table, indexMap) ->
            result.put(table, new ArrayList<>(indexMap.values())));
        return result;
    }

    // ---- helpers ----

    private Map<String, ColumnInfo> toMap(List<ColumnInfo> cols) {
        Map<String, ColumnInfo> m = new LinkedHashMap<>();
        for (ColumnInfo c : cols) m.put(c.columnName(), c);
        return m;
    }

    private Map<String, IndexInfo> toIndexMap(List<IndexInfo> indexes) {
        Map<String, IndexInfo> m = new LinkedHashMap<>();
        for (IndexInfo i : indexes) m.put(i.indexName(), i);
        return m;
    }

    private boolean columnChanged(ColumnInfo cur, ColumnInfo nw) {
        return !Objects.equals(cur.dataType(),            nw.dataType())
            || !Objects.equals(cur.isNullable(),          nw.isNullable())
            || !Objects.equals(cur.columnDefault(),       nw.columnDefault())
            || !Objects.equals(cur.characterMaxLength(),  nw.characterMaxLength());
    }

    private ColumnDiff toColumnDiff(String table, ColumnInfo col, ColumnInfo previous) {
        return new ColumnDiff(
            table, col.columnName(), col.dataType(), col.isNullable(),
            col.columnDefault(), col.characterMaxLength(),
            previous != null ? previous.dataType()   : null,
            previous != null ? previous.isNullable() : null
        );
    }

    private IndexDiff toIndexDiff(String table, IndexInfo idx) {
        return new IndexDiff(table, idx.indexName(), idx.columns(), idx.unique());
    }

    // ---- internal data carriers ----

    record TableInfo(String tableName, String tableType) {}

    record ColumnInfo(String columnName, String dataType, String isNullable,
                      String columnDefault, Long characterMaxLength, int ordinalPosition) {}

    // Mutable columns list so we can build it incrementally while reading the ResultSet
    static final class IndexInfo {
        private final String indexName;
        private final List<String> columns;
        private final boolean unique;

        IndexInfo(String indexName, List<String> columns, boolean unique) {
            this.indexName = indexName;
            this.columns   = columns;
            this.unique    = unique;
        }

        String       indexName() { return indexName; }
        List<String> columns()   { return columns; }
        boolean      unique()    { return unique; }
    }
}

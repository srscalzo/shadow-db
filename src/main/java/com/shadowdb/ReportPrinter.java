package com.shadowdb;

import com.shadowdb.model.ColumnDiff;
import com.shadowdb.model.IndexDiff;
import com.shadowdb.model.SchemaDiff;
import picocli.CommandLine.Help.Ansi;

import java.util.List;

public class ReportPrinter {

    private static final Ansi ANSI = Ansi.AUTO;

    public int printReport(SchemaDiff diff) {
        printHeader(diff);

        printTablesSection(diff);
        printColumnsSection(diff);
        printIndexesSection(diff);

        if (!diff.hasChanges()) {
            System.out.println(bold("No schema changes detected."));
        }

        return diff.hasChanges() ? 1 : 0;
    }

    private void printHeader(SchemaDiff diff) {
        System.out.println(bold("Shadow DB Migration Preview"));
        System.out.println(bold("============================"));
        System.out.println("New migration: " + bold(diff.newMigrationFilename()));
        System.out.println();
    }

    private void printTablesSection(SchemaDiff diff) {
        if (diff.tablesAdded().isEmpty() && diff.tablesRemoved().isEmpty()) {
            System.out.println(dim("No table changes."));
            return;
        }

        System.out.println(bold("TABLES"));
        for (String table : diff.tablesAdded()) {
            System.out.println("  " + green("+ " + table) + "  (added)");
        }
        for (String table : diff.tablesRemoved()) {
            System.out.println("  " + red("- " + table) + "  (removed)");
        }
        System.out.println();
    }

    private void printColumnsSection(SchemaDiff diff) {
        if (diff.columnsAdded().isEmpty() && diff.columnsRemoved().isEmpty() && diff.columnsModified().isEmpty()) {
            System.out.println(dim("No column changes."));
            System.out.println();
            return;
        }

        // Group by table for readable output
        List<String> tables = diff.columnsAdded().stream().map(ColumnDiff::tableName).distinct().toList();
        for (String table : tables) {
            System.out.println(bold("COLUMNS in '" + table + "'"));
            diff.columnsAdded().stream()
                .filter(c -> c.tableName().equals(table))
                .forEach(c -> System.out.println("  " + green("+ " + formatColumn(c))));
        }
        if (!diff.columnsAdded().isEmpty()) System.out.println();

        List<String> removedTables = diff.columnsRemoved().stream().map(ColumnDiff::tableName).distinct().toList();
        for (String table : removedTables) {
            System.out.println(bold("COLUMNS REMOVED from '" + table + "'"));
            diff.columnsRemoved().stream()
                .filter(c -> c.tableName().equals(table))
                .forEach(c -> System.out.println("  " + red("- " + formatColumn(c))));
            System.out.println();
        }

        List<String> modifiedTables = diff.columnsModified().stream().map(ColumnDiff::tableName).distinct().toList();
        for (String table : modifiedTables) {
            System.out.println(bold("COLUMNS MODIFIED in '" + table + "'"));
            diff.columnsModified().stream()
                .filter(c -> c.tableName().equals(table))
                .forEach(c -> System.out.println("  " + yellow("~ " + formatModifiedColumn(c))));
            System.out.println();
        }
    }

    private void printIndexesSection(SchemaDiff diff) {
        if (diff.indexesAdded().isEmpty() && diff.indexesRemoved().isEmpty()) {
            System.out.println(dim("No index changes."));
            return;
        }

        List<String> addedTables = diff.indexesAdded().stream().map(IndexDiff::tableName).distinct().toList();
        for (String table : addedTables) {
            System.out.println(bold("INDEXES in '" + table + "'"));
            diff.indexesAdded().stream()
                .filter(i -> i.tableName().equals(table))
                .forEach(i -> System.out.println("  " + green("+ " + formatIndex(i))));
            System.out.println();
        }

        List<String> removedTables = diff.indexesRemoved().stream().map(IndexDiff::tableName).distinct().toList();
        for (String table : removedTables) {
            System.out.println(bold("INDEXES REMOVED from '" + table + "'"));
            diff.indexesRemoved().stream()
                .filter(i -> i.tableName().equals(table))
                .forEach(i -> System.out.println("  " + red("- " + formatIndex(i))));
            System.out.println();
        }
    }

    private String formatColumn(ColumnDiff c) {
        StringBuilder sb = new StringBuilder();
        sb.append(padRight(c.columnName(), 20)).append(" ").append(c.dataType().toUpperCase());
        if (c.characterMaxLength() != null) sb.append("(").append(c.characterMaxLength()).append(")");
        if ("NO".equalsIgnoreCase(c.isNullable())) sb.append(" NOT NULL");
        if (c.columnDefault() != null) sb.append(" DEFAULT ").append(c.columnDefault());
        return sb.toString();
    }

    private String formatModifiedColumn(ColumnDiff c) {
        return padRight(c.columnName(), 20)
            + " " + c.previousDataType() + " -> " + c.dataType()
            + (c.previousNullable() != null ? "  (nullable: " + c.previousNullable() + " -> " + c.isNullable() + ")" : "");
    }

    private String formatIndex(IndexDiff i) {
        String cols = String.join(", ", i.columns());
        return i.indexName() + " (" + cols + ")" + (i.unique() ? " UNIQUE" : "");
    }

    private String padRight(String s, int width) {
        return String.format("%-" + width + "s", s);
    }

    private String green(String text)  { return ANSI.string("@|green "  + text + "|@"); }
    private String red(String text)    { return ANSI.string("@|red "    + text + "|@"); }
    private String yellow(String text) { return ANSI.string("@|yellow " + text + "|@"); }
    private String bold(String text)   { return ANSI.string("@|bold "   + text + "|@"); }
    private String dim(String text)    { return ANSI.string("@|faint "  + text + "|@"); }
}

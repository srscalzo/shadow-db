package com.shadowdb.model;

import java.util.List;

public record SchemaDiff(
    List<String>     tablesAdded,
    List<String>     tablesRemoved,
    List<ColumnDiff> columnsAdded,
    List<ColumnDiff> columnsRemoved,
    List<ColumnDiff> columnsModified,
    List<IndexDiff>  indexesAdded,
    List<IndexDiff>  indexesRemoved,
    String           newMigrationFilename
) {
    public boolean hasChanges() {
        return !tablesAdded.isEmpty() || !tablesRemoved.isEmpty()
            || !columnsAdded.isEmpty() || !columnsRemoved.isEmpty()
            || !columnsModified.isEmpty()
            || !indexesAdded.isEmpty() || !indexesRemoved.isEmpty();
    }
}

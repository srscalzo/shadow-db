package com.shadowdb.model;

public record ColumnDiff(
    String tableName,
    String columnName,
    String dataType,
    String isNullable,
    String columnDefault,
    Long   characterMaxLength,
    String previousDataType,
    String previousNullable
) {}

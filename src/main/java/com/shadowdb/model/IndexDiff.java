package com.shadowdb.model;

import java.util.List;

public record IndexDiff(
    String       tableName,
    String       indexName,
    List<String> columns,
    boolean      unique
) {}

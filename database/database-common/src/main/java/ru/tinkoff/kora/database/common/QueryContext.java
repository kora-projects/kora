package ru.tinkoff.kora.database.common;

import java.util.Objects;

public record QueryContext(String queryId, String sql, String operation) {
    public QueryContext {
        Objects.requireNonNull(queryId);
        Objects.requireNonNull(sql);
        Objects.requireNonNull(operation);
    }

    public QueryContext(String queryId, String sql) {
        this(queryId, sql, "db_query");
    }
}

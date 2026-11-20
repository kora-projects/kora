package io.koraframework.database.jdbc.telemetry;

import java.util.Objects;

public record JdbcTransactionContext(String isolationLevel) {
    public JdbcTransactionContext {
        Objects.requireNonNull(isolationLevel);
    }
}

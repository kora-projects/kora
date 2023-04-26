package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

public interface DataBaseLogger {
    boolean isEnabled();

    void logQueryBegin(QueryContext queryContext);

    void logQueryEnd(long duration, QueryContext queryContext, @Nullable Throwable ex);
}

package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.QueryContext;

public interface DataBaseLogger {
    boolean isEnabled();

    void logQueryBegin(QueryContext queryContext);

    void logQueryEnd(long processingTime, QueryContext queryContext, @Nullable Throwable ex);
}

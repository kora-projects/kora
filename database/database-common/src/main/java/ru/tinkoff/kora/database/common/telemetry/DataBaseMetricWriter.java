package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.QueryContext;

public interface DataBaseMetricWriter {
    void recordQuery(long queryBegin, QueryContext queryContext, @Nullable Throwable exception);

    Object getMetricRegistry();
}

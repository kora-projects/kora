package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

public interface DataBaseMetricWriter {
    void recordQuery(long queryBegin, QueryContext queryContext, @Nullable Throwable exception);

    Object getMetricRegistry();
}

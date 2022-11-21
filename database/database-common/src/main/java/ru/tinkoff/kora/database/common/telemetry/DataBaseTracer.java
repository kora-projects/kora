package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

public interface DataBaseTracer {
    interface DataBaseSpan {
        void close(long duration, @Nullable Throwable ex);
    }

    DataBaseSpan createQuerySpan(Context ctx, QueryContext queryContext);

    DataBaseSpan createCallSpan(QueryContext queryContext);
}

package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;

public interface DataBaseTracer {
    interface DataBaseSpan {
        void close(@Nullable Throwable ex);
    }

    DataBaseSpan createQuerySpan(Context ctx, QueryContext queryContext);

    DataBaseSpan createCallSpan(QueryContext queryContext);
}

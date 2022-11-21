package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;

import javax.annotation.Nullable;

public interface DataBaseTelemetry {
    @Nullable
    Object getMetricRegistry();

    interface DataBaseTelemetryContext {
        void close(@Nullable Throwable exception);
    }

    DataBaseTelemetryContext createContext(Context context, QueryContext query);
}

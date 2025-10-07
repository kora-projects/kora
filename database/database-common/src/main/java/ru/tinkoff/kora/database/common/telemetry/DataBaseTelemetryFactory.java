package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface DataBaseTelemetryFactory {

    DataBaseTelemetry.DataBaseTelemetryContext EMPTY_CTX = exception -> {};
    DataBaseTelemetry EMPTY = new DataBaseTelemetry() {
        @Nullable
        @Override
        public Object getMetricRegistry() {
            return null;
        }

        @Override
        public DataBaseTelemetryContext createContext(Context context, QueryContext query) {
            return EMPTY_CTX;
        }
    };

    @Deprecated
    default DataBaseTelemetry get(TelemetryConfig config, String name, String driverType, String dbType, String username) {
        return EMPTY;
    }

    default DataBaseTelemetry get(TelemetryConfig config, String name, String driverType, String dbType, String username, @Nullable String connectionString) {
        return get(config, name, driverType, dbType, username);
    }
}

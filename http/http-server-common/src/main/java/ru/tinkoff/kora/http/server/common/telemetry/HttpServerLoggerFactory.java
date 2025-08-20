package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

public interface HttpServerLoggerFactory {

    /**
     * @see #get(HttpServerLoggerConfig, HttpServerConfig)
     */
    @Deprecated
    @Nullable
    default HttpServerLogger get(HttpServerLoggerConfig loggerConfig) {
        return null;
    }

    @Nullable
    default HttpServerLogger get(HttpServerLoggerConfig loggerConfig, HttpServerConfig serverConfig) {
        return get(loggerConfig);
    }
}

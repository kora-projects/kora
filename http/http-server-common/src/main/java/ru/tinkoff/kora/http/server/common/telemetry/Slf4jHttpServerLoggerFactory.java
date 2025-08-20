package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;

import java.util.Objects;

public final class Slf4jHttpServerLoggerFactory implements HttpServerLoggerFactory {

    @Deprecated
    @Nullable
    @Override
    public HttpServerLogger get(HttpServerLoggerConfig loggerConfig) {
        return this.get(loggerConfig, null);
    }

    @Nullable
    @Override
    public HttpServerLogger get(HttpServerLoggerConfig loggerConfig, @Nullable HttpServerConfig serverConfig) {
        if (Objects.requireNonNullElse(loggerConfig.enabled(), false)) {
            return new Slf4jHttpServerLogger(loggerConfig.stacktrace(), loggerConfig.maskQueries(),
                loggerConfig.maskHeaders(), loggerConfig.mask(), loggerConfig.pathTemplate(), serverConfig);
        } else {
            return null;
        }
    }
}

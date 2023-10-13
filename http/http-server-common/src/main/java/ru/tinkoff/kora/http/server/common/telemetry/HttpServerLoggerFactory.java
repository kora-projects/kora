package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;

public interface HttpServerLoggerFactory {
    @Nullable
    HttpServerLogger get(HttpServerLoggerConfig logging);
}

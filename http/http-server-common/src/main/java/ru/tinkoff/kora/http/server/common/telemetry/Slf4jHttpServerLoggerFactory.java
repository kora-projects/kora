package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;

import java.util.Objects;

public final class Slf4jHttpServerLoggerFactory implements HttpServerLoggerFactory {

    @Nullable
    @Override
    public HttpServerLogger get(HttpServerLoggerConfig logging) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new Slf4jHttpServerLogger(logging.stacktrace(), logging.maskQueries(),
                logging.maskHeaders(), logging.mask(), logging.pathTemplate());
        } else {
            return null;
        }
    }
}

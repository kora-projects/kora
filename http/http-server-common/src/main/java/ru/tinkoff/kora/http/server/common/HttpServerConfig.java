package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface HttpServerConfig {

    default int port() {
        return 8080;
    }

    default boolean ignoreTrailingSlash() {
        return false;
    }

    default Duration socketReadTimeout() {
        return Duration.ZERO;
    }

    default Duration socketWriteTimeout() {
        return Duration.ZERO;
    }

    default boolean socketKeepAliveEnabled() {
        return false;
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    HttpServerTelemetryConfig telemetry();
}

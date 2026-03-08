package io.koraframework.http.server.common;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;

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

    default Size maxRequestBodySize() {
        return Size.of(256, Size.Type.MiB);
    }
}

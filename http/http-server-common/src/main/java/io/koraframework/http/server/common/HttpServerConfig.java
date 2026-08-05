package io.koraframework.http.server.common;

import io.koraframework.common.util.Size;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;

import java.time.Duration;

@ConfigMapper
public interface HttpServerConfig {

    default int port() {
        return 8080;
    }

    /**
     * @return Whether to ignore a trailing slash in the path, so that /my/path and /my/path/ are treated as the same route.
     */
    default boolean ignoreTrailingSlash() {
        return false;
    }

    /**
     * @return Maximum time to wait for reading data from a socket or connection, zero disables the timeout.
     */
    default Duration socketReadTimeout() {
        return Duration.ZERO;
    }

    /**
     * @return Maximum time to wait for writing data to a socket or connection, zero disables the timeout.
     */
    default Duration socketWriteTimeout() {
        return Duration.ZERO;
    }

    /**
     * @return Whether to enable TCP keep-alive for a socket or connection.
     */
    default boolean socketKeepAliveEnabled() {
        return false;
    }

    default boolean headerKeepAliveEnabled() {
        return false;
    }

    default boolean headerServerDateEnabled() {
        return true;
    }

    /**
     * @return Time to wait for request processing before server shutdown during graceful shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * @return Maximum allowed size of an incoming request body.
     */
    default Size maxRequestBodySize() {
        return Size.of(256, Size.Type.MiB);
    }

    /**
     * @return Telemetry configuration for logging, metrics and tracing of incoming requests.
     */
    HttpServerTelemetryConfig telemetry();
}

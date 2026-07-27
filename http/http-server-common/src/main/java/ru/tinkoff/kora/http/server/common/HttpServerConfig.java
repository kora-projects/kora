package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface HttpServerConfig {

    /**
     * @return Public HTTP server port.
     */
    default int publicApiHttpPort() {
        return 8080;
    }

    /**
     * @return Private HTTP server port.
     */
    default int privateApiHttpPort() {
        return 8085;
    }

    /**
     * @return Path to get metrics on the private server.
     */
    default String privateApiHttpMetricsPath() {
        return "/metrics";
    }

    /**
     * @return Path to get readiness probe status on the private server.
     */
    default String privateApiHttpReadinessPath() {
        return "/system/readiness";
    }

    /**
     * @return Path to get liveness probe status on the private server.
     */
    default String privateApiHttpLivenessPath() {
        return "/system/liveness";
    }

    /**
     * @return Whether to ignore a trailing slash in the path, so that /my/path and /my/path/ are treated as the same route.
     */
    default boolean ignoreTrailingSlash() {
        return false;
    }

    /**
     * @return Number of network I/O threads.
     */
    default int ioThreads() {
        return Math.max(Runtime.getRuntime().availableProcessors(), 2);
    }

    /**
     * @return Number of threads for blocking request processing.
     */
    default int blockingThreads() {
        return Math.min(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8, 200);
    }

    /**
     * @return Maximum idle lifetime of a request handler thread.
     */
    default Duration threadKeepAliveTimeout() {
        return Duration.ofSeconds(60);
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

    /**
     * @return Time to wait for request processing before server shutdown during graceful shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * @return Telemetry configuration for logging, metrics and tracing of incoming requests.
     */
    HttpServerTelemetryConfig telemetry();

    /**
     * @return Maximum allowed size of an incoming request body.
     */
    default Size maxRequestBodySize() {
        return Size.of(256, Size.Type.MiB);
    }
}

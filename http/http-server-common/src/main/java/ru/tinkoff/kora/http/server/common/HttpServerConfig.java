package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface HttpServerConfig {
    default int publicApiHttpPort() {return 8080;}

    default int privateApiHttpPort() {return 8085;}

    default String privateApiHttpMetricsPath() {return "/metrics";}

    default String privateApiHttpReadinessPath() {return "/system/readiness";}

    default String privateApiHttpLivenessPath() {return "/system/liveness";}

    default boolean ignoreTrailingSlash() {return false;}

    default int ioThreads() {return Math.max(Runtime.getRuntime().availableProcessors(), 2);}

    default int blockingThreads() {return Math.min(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8, 200);}

    default Duration shutdownWait() {return Duration.ofMillis(100);}
}

package ru.tinkoff.kora.http.server.common;

import javax.annotation.Nullable;

public record HttpServerConfig(
    int publicApiHttpPort,
    int privateApiHttpPort,
    String privateApiHttpMetricsPath,
    String privateApiHttpReadinessPath,
    String privateApiHttpLivenessPath,
    int ioThreads,
    int blockingThreads,
    int shutdownWait) {

    public static int DEFAULT_PUBLIC_API_PORT = 8080;
    public static int DEFAULT_PRIVATE_API_PORT = 8085;
    public static String DEFAULT_PRIVATE_API_METRICS_PATH = "/metrics";
    public static String DEFAULT_PRIVATE_API_READINESS_PATH = "/system/readiness";
    public static String DEFAULT_PRIVATE_API_LIVENESS_PATH = "/system/liveness";
    public static int DEFAULT_IO_THREADS = Math.max(Runtime.getRuntime().availableProcessors(), 2);
    public static int DEFAULT_BLOCKING_THREADS = Math.min(Math.max(Runtime.getRuntime().availableProcessors(), 2) * 8, 200);
    public static int DEFAULT_SHUTDOWN_WAIT = 5000;

    public HttpServerConfig(
        @Nullable Integer publicApiHttpPort,
        @Nullable Integer privateApiHttpPort,
        @Nullable String  privateApiHttpMetricsPath,
        @Nullable String  privateApiHttpReadinessPath,
        @Nullable String  privateApiHttpLivenessPath,
        @Nullable Integer ioThreads,
        @Nullable Integer blockingThreads,
        @Nullable Integer shutdownWait) {
        this(
            publicApiHttpPort != null ? publicApiHttpPort : DEFAULT_PUBLIC_API_PORT,
            privateApiHttpPort != null ? privateApiHttpPort : DEFAULT_PRIVATE_API_PORT,
            privateApiHttpMetricsPath != null ? privateApiHttpMetricsPath : DEFAULT_PRIVATE_API_METRICS_PATH,
            privateApiHttpReadinessPath != null ? privateApiHttpReadinessPath : DEFAULT_PRIVATE_API_READINESS_PATH,
            privateApiHttpLivenessPath != null ? privateApiHttpLivenessPath : DEFAULT_PRIVATE_API_LIVENESS_PATH,
            ioThreads != null ? ioThreads : DEFAULT_IO_THREADS,
            blockingThreads != null ? blockingThreads : DEFAULT_BLOCKING_THREADS,
            shutdownWait != null ? shutdownWait : DEFAULT_SHUTDOWN_WAIT
        );
    }
}

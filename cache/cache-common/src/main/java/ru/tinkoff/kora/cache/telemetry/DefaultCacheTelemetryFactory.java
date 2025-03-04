package ru.tinkoff.kora.cache.telemetry;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class DefaultCacheTelemetryFactory implements CacheTelemetryFactory {

    private static final CacheTelemetry.CacheTelemetryContext EMPTY_CONTEXT = new CacheTelemetry.CacheTelemetryContext() {
        @Override
        public void recordSuccess(@Nullable Object valueFromCache) {

        }

        @Override
        public void recordFailure(@Nullable Throwable throwable) {

        }
    };
    private static final CacheTelemetry EMPTY_TELEMETRY = operationName -> EMPTY_CONTEXT;

    @Nullable
    private final CacheLoggerFactory loggerFactory;
    @Nullable
    private final CacheTracerFactory tracingFactory;
    @Nullable
    private final CacheMetricsFactory metricsFactory;

    public DefaultCacheTelemetryFactory(@Nullable CacheLoggerFactory loggerFactory,
                                        @Nullable CacheMetricsFactory metricsFactory,
                                        @Nullable CacheTracerFactory tracingFactory) {
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
        this.tracingFactory = tracingFactory;
    }

    @Override
    public CacheTelemetry get(TelemetryConfig config, CacheTelemetryArgs args) {
        var tracing = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), args);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), args);
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), args);
        if (tracing == null && metrics == null && logger == null) {
            return EMPTY_TELEMETRY;
        }

        return new DefaultCacheTelemetry(args, tracing, metrics, logger);
    }

    private record Operation(String name, String cacheName, String origin) implements CacheTelemetryOperation {}

    private static final class DefaultCacheTelemetry implements CacheTelemetry {

        private final CacheTelemetryArgs args;
        @Nullable
        private final CacheTracer tracer;
        @Nullable
        private final CacheMetrics metrics;
        @Nullable
        private final CacheLogger logger;

        public DefaultCacheTelemetry(CacheTelemetryArgs args,
                                     @Nullable CacheTracer tracer,
                                     @Nullable CacheMetrics metrics,
                                     @Nullable CacheLogger logger) {
            this.args = args;
            this.tracer = tracer;
            this.metrics = metrics;
            this.logger = logger;
        }

        @Override
        public CacheTelemetryContext get(@Nonnull String operationName) {
            var operation = new Operation(operationName, args.cacheName(), args.origin());

            var startedInNanos = System.nanoTime();
            if (logger != null) {
                logger.logStart(operation);
            }

            final CacheTracer.CacheSpan span = (tracer != null)
                ? tracer.trace(operation)
                : null;

            return new CacheTelemetryContext() {
                @Override
                public void recordSuccess(@Nullable Object valueFromCache) {
                    if (metrics != null) {
                        final long durationInNanos = System.nanoTime() - startedInNanos;
                        metrics.recordSuccess(operation, durationInNanos, valueFromCache);
                    }
                    if (span != null) {
                        span.recordSuccess();
                    }
                }

                @Override
                public void recordFailure(@Nullable Throwable throwable) {
                    if (metrics != null) {
                        final long durationInNanos = System.nanoTime() - startedInNanos;
                        metrics.recordFailure(operation, durationInNanos, throwable);
                    }
                    if (span != null) {
                        span.recordFailure(throwable);
                    }
                }
            };
        }
    }
}

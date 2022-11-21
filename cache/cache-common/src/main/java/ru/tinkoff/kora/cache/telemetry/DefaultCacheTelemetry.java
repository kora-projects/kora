package ru.tinkoff.kora.cache.telemetry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DefaultCacheTelemetry implements CacheTelemetry {

    private static final TelemetryContext STUB_CONTEXT = new StubCacheTelemetry();

    @Nullable
    private final CacheMetrics metrics;
    @Nullable
    private final CacheTracer tracer;
    private final boolean isStubTelemetry;

    public DefaultCacheTelemetry(@Nullable CacheMetrics metrics, @Nullable CacheTracer tracer) {
        this.metrics = metrics;
        this.tracer = tracer;
        this.isStubTelemetry = metrics == null && tracer == null;
    }

    record StubCacheTelemetry() implements TelemetryContext {
        @Override
        public void startRecording() {}

        @Override
        public void recordSuccess() {}

        @Override
        public void recordSuccess(@Nullable Object valueFromCache) {}

        @Override
        public void recordFailure(@Nullable Throwable throwable) {}
    }

    class DefaultCacheTelemetryContext implements TelemetryContext {

        private final Operation operation;

        private CacheTracer.CacheSpan span;
        private long startedInNanos = -1;

        DefaultCacheTelemetryContext(Operation operation) {
            this.operation = operation;
        }

        @Override
        public void startRecording() {
            if (startedInNanos == -1) {
                startedInNanos = System.nanoTime();

                if (tracer != null) {
                    span = tracer.trace(operation);
                }
            }
        }

        @Override
        public void recordSuccess() {
            recordSuccess(null);
        }

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
    }

    @Nonnull
    @Override
    public TelemetryContext create(@Nonnull Operation.Type type, @Nonnull String cacheName, @Nonnull String origin) {
        if (isStubTelemetry) {
            return STUB_CONTEXT;
        }

        return new DefaultCacheTelemetryContext(new Operation(type, cacheName, origin));
    }
}

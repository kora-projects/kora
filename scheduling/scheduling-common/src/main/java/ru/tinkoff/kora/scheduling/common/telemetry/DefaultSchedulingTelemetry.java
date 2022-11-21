package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.Context;

import javax.annotation.Nullable;

public final class DefaultSchedulingTelemetry implements SchedulingTelemetry {
    @Nullable
    private final SchedulingMetrics metrics;
    @Nullable
    private final SchedulingTracer tracer;
    @Nullable
    private final SchedulingLogger logger;

    public DefaultSchedulingTelemetry(@Nullable SchedulingMetrics metrics, @Nullable SchedulingTracer tracer, @Nullable SchedulingLogger logger) {
        this.metrics = metrics;
        this.tracer = tracer;
        this.logger = logger;
    }

    @Override
    public SchedulingTelemetryContext get(Context ctx) {
        var span = this.tracer == null ? null : this.tracer.createSpan(ctx);
        if (this.logger != null) {
            this.logger.logJobStart();
        }

        return new DefaultTelemetryContext(this.metrics, span, this.logger);
    }

    private static class DefaultTelemetryContext implements SchedulingTelemetryContext {
        private final long start = System.nanoTime();
        @Nullable
        private final SchedulingMetrics metrics;
        @Nullable
        private final SchedulingTracer.SchedulingSpan span;
        @Nullable
        private final SchedulingLogger logger;

        private DefaultTelemetryContext(@Nullable SchedulingMetrics metrics, @Nullable SchedulingTracer.SchedulingSpan span, @Nullable SchedulingLogger logger) {
            this.metrics = metrics;
            this.span = span;
            this.logger = logger;
        }


        @Override
        public void close(@Nullable Throwable exception) {
            var end = System.nanoTime();
            var duration = end - start;
            if (this.metrics != null) {
                this.metrics.record(duration, exception);
            }
            if (this.logger != null) {
                this.logger.logJobFinish(duration, exception);
            }
            if (this.span != null) {
                this.span.close(duration, exception);
            }
        }
    }
}

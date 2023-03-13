package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.Context;

import javax.annotation.Nullable;
import java.util.Objects;

public final class DefaultSchedulingTelemetry implements SchedulingTelemetry {
    @Nullable
    private final SchedulingMetrics metrics;
    @Nullable
    private final SchedulingTracer tracer;
    @Nullable
    private final SchedulingLogger logger;
    private final Class<?> jobClass;
    private final String jobMethod;

    public DefaultSchedulingTelemetry(Class<?> jobClass, String jobMethod, @Nullable SchedulingMetrics metrics, @Nullable SchedulingTracer tracer, @Nullable SchedulingLogger logger) {
        this.metrics = metrics;
        this.tracer = tracer;
        this.logger = logger;
        this.jobClass = Objects.requireNonNull(jobClass);
        this.jobMethod = Objects.requireNonNull(jobMethod);
    }

    @Override
    public Class<?> jobClass() {
        return this.jobClass;
    }

    @Override
    public String jobMethod() {
        return this.jobMethod;
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

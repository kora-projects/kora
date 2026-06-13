package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;

public class DefaultSchedulingObservation implements SchedulingObservation {

    protected final long start = System.nanoTime();
    protected final DefaultSchedulingTelemetry.TelemetryContext context;
    protected final Span span;
    protected final DefaultSchedulingLoggerFactory.DefaultSchedulingLogger logger;
    protected final DefaultSchedulingMetricsFactory.DefaultSchedulingMetrics metrics;
    @Nullable
    protected Throwable error;

    public DefaultSchedulingObservation(DefaultSchedulingTelemetry.TelemetryContext context,
                                        Span span,
                                        DefaultSchedulingLoggerFactory.DefaultSchedulingLogger logger,
                                        DefaultSchedulingMetricsFactory.DefaultSchedulingMetrics metrics) {
        this.context = context;
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeRun() {
        this.logger.logStart();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void end() {
        var durationInNanos = System.nanoTime() - start;

        this.metrics.record(this.error, durationInNanos);
        this.logger.logEnd(this.error, durationInNanos);
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }
}

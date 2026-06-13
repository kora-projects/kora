package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;

public class DefaultZeebeWorkerObservation implements ZeebeWorkerObservation {

    protected final long started = System.nanoTime();
    protected final ActivatedJob job;
    protected final DefaultZeebeWorkerTelemetry.TelemetryContext context;
    protected final Span span;
    protected final DefaultZeebeWorkerLoggerFactory.DefaultZeebeWorkerLogger logger;
    protected final DefaultZeebeWorkerMetricsFactory.DefaultZeebeWorkerMetrics metrics;
    protected Throwable error;
    protected boolean failedByUser;

    public DefaultZeebeWorkerObservation(ActivatedJob job,
                                         DefaultZeebeWorkerTelemetry.TelemetryContext context,
                                         Span span,
                                         DefaultZeebeWorkerLoggerFactory.DefaultZeebeWorkerLogger logger,
                                         DefaultZeebeWorkerMetricsFactory.DefaultZeebeWorkerMetrics metrics) {
        this.job = job;
        this.context = context;
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public void observeFinalCommandStep(FinalCommandStep<?> command) {
        if (command instanceof ThrowErrorCommandStep1.ThrowErrorCommandStep2 || command instanceof FailJobCommandStep1.FailJobCommandStep2) {
            this.failedByUser = true;
            this.span.setStatus(StatusCode.ERROR);
        }
    }

    @Override
    public void observeHandle(String type, ActivatedJob job) {
        this.logger.logJobHandle(job);
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeError(Throwable e) {
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
        this.error = e;
    }

    @Override
    public void end() {
        var processingTimeNanos = System.nanoTime() - this.started;
        this.metrics.record(job, error, failedByUser, processingTimeNanos);
        this.logger.logJobEnd(job, error, failedByUser, processingTimeNanos);
        if (this.error == null && !this.failedByUser) {
            this.span.setStatus(StatusCode.OK);
        } else {
            var errorType = this.error == null ? "ErrorStep" : this.error.getClass().getCanonicalName();
            this.span.setStatus(StatusCode.ERROR, errorType);
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorType);
        }
        this.span.end();
    }
}

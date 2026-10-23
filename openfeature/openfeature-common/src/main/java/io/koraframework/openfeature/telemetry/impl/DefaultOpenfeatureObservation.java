package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.HookContext;
import io.koraframework.openfeature.telemetry.OpenfeatureObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;

public class DefaultOpenfeatureObservation implements OpenfeatureObservation {

    protected final DefaultOpenfeatureTelemetry.TelemetryContext context;
    protected final DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger logger;
    protected final DefaultOpenfeatureMetricsFactory.DefaultOpenfeatureMetrics metrics;
    protected final HookContext<?> evaluation;
    protected final Span span;
    protected final long startNanos = System.nanoTime();

    @Nullable
    protected FlagEvaluationDetails<?> result;
    @Nullable
    protected Throwable exception;
    protected boolean ended;

    public DefaultOpenfeatureObservation(DefaultOpenfeatureTelemetry.TelemetryContext context,
                                         DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger logger,
                                         DefaultOpenfeatureMetricsFactory.DefaultOpenfeatureMetrics metrics,
                                         HookContext<?> evaluation,
                                         Span span) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.evaluation = evaluation;
        this.span = span;
        this.logger.logEvaluation(evaluation);
    }

    @Override
    public void observeResult(FlagEvaluationDetails<?> result) {
        this.result = result;
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (this.ended) {
            return;
        }
        this.ended = true;
        var durationNanos = System.nanoTime() - this.startNanos;
        try {
            if (this.result == null || this.result.getErrorCode() != null || this.exception != null) {
                this.metrics.recordFailure(this.evaluation, this.result, this.exception, durationNanos);
                this.logger.logError(this.evaluation, this.result, this.exception, durationNanos);
            } else {
                this.metrics.recordSuccess(this.evaluation, this.result, durationNanos);
                this.logger.logResult(this.evaluation, this.result, durationNanos);
            }
        } finally {
            try {
                completeSpan();
            } finally {
                this.span.end();
            }
        }
    }

    protected void completeSpan() {
        if (this.result == null || this.result.getErrorCode() != null || this.exception != null) {
            this.span.setStatus(StatusCode.ERROR);
            this.span.setAttribute("error.type", DefaultOpenfeatureMetricsFactory.errorType(this.result, this.exception));
        }
        if (this.result != null && this.result.getVariant() != null) {
            this.span.setAttribute("feature_flag.result.variant", this.result.getVariant());
        }
    }
}

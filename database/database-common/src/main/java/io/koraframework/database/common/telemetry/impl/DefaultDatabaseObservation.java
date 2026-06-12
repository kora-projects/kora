package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;

public class DefaultDatabaseObservation implements DatabaseObservation {

    protected final QueryContext query;
    protected final DefaultDatabaseTelemetry.TelemetryContext context;
    protected final DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger;
    protected final DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics;
    protected final Span span;
    protected final long started = System.nanoTime();
    protected long statementStarted = started;
    protected Throwable error;

    public DefaultDatabaseObservation(QueryContext query,
                                      DefaultDatabaseTelemetry.TelemetryContext context,
                                      DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger,
                                      DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics,
                                      Span span) {
        this.query = query;
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.span = span;
    }

    @Override
    public void observeConnection() {
        this.span.addEvent("connection");
    }

    @Override
    public void observeStatement() {
        this.statementStarted = System.nanoTime();
        this.span.addEvent("statement");
        this.logger.logQueryBegin(query);
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeError(Throwable e) {
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
        this.error = e;
    }

    @Override
    public void end() {
        var processingTimeNanos = System.nanoTime() - this.statementStarted;
        this.span.addEvent("result");
        this.metrics.record(query, error, processingTimeNanos);
        this.logger.logQueryEnd(query, error, processingTimeNanos);
        if (error == null) {
            this.span.setStatus(StatusCode.OK);
        } else {
            var errorValue = this.error.getClass().getCanonicalName();
            this.span.setStatus(StatusCode.ERROR, errorValue);
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);
        }
        this.span.end();
    }
}

package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;

public class DefaultDatabaseObservation implements DatabaseObservation {

    private static final ThreadLocal<DefaultDatabaseObservation> RECYCLER = new ThreadLocal<>();

    public static DefaultDatabaseObservation recycle(QueryContext query,
                                                     DefaultDatabaseTelemetry.TelemetryContext context,
                                                     DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger,
                                                     DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics,
                                                     Span span) {
        DefaultDatabaseObservation instance = RECYCLER.get();
        if (instance == null) {
            instance = new DefaultDatabaseObservation();
            RECYCLER.set(instance);
        }
        instance.reset(query, context, logger, metrics, span);
        return instance;
    }

    protected QueryContext query;
    protected DefaultDatabaseTelemetry.TelemetryContext context;
    protected DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger;
    protected DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics;
    protected Span span;
    protected long started;
    protected long statementStarted;
    protected Throwable error;

    private DefaultDatabaseObservation() {}

    protected void reset(QueryContext query,
                         DefaultDatabaseTelemetry.TelemetryContext context,
                         DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger,
                         DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics,
                         Span span) {
        this.query = query;
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.span = span;
        this.started = System.nanoTime();
        this.statementStarted = started;
        this.error = null;
    }

    @Override
    public void observeConnection() {
        this.span.addEvent("db.connection");
    }

    @Override
    public void observeStatement() {
        this.statementStarted = System.nanoTime();
        this.span.addEvent("db.statement");
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
        this.span.addEvent("db.result");
        this.metrics.record(query, error, processingTimeNanos);
        this.logger.logQueryEnd(query, error, processingTimeNanos);
        if (error == null) {
            this.span.setStatus(StatusCode.OK);
        } else {
            var errorValue = this.error.getClass().getCanonicalName();
            if (errorValue == null) {
                errorValue = this.error.getClass().getName();
            }
            this.span.setStatus(StatusCode.ERROR, errorValue);
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);
        }
        this.span.end();
        this.query = null;
        this.context = null;
        this.logger = null;
        this.metrics = null;
        this.span = null;
        this.error = null;
    }
}

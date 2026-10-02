package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import io.koraframework.database.jdbc.telemetry.JdbcTransactionObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

public class DefaultJdbcTransactionObservation implements JdbcTransactionObservation {

    protected final JdbcTransactionContext transaction;
    protected final DefaultJdbcDatabaseTelemetry.TelemetryContext context;
    protected final DefaultJdbcDatabaseLoggerFactory.DefaultJdbcDatabaseLogger logger;
    protected final DefaultJdbcDatabaseMetricsFactory.DefaultJdbcDatabaseMetrics metrics;
    protected final Span span;
    protected final long started = System.nanoTime();
    protected boolean committed;
    @Nullable
    protected Throwable error;

    public DefaultJdbcTransactionObservation(JdbcTransactionContext transaction,
                                             DefaultJdbcDatabaseTelemetry.TelemetryContext context,
                                             DefaultJdbcDatabaseLoggerFactory.DefaultJdbcDatabaseLogger logger,
                                             DefaultJdbcDatabaseMetricsFactory.DefaultJdbcDatabaseMetrics metrics,
                                             Span span) {
        this.transaction = transaction;
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.span = span;
        this.metrics.recordTransactionStart(transaction);
        this.logger.logTransactionBegin(transaction);
    }

    @Override
    public void observeCommit() {
        this.committed = true;
        this.span.addEvent("commit");
    }

    @Override
    public void observeRollback() {
        this.committed = false;
        this.span.addEvent("rollback");
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
        var processingTimeNanos = System.nanoTime() - this.started;
        this.metrics.recordTransactionEnd(transaction, error, processingTimeNanos);
        this.logger.logTransactionEnd(transaction, committed, error, processingTimeNanos);
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

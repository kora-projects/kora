package ru.tinkoff.kora.database.common.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.slf4j.Logger;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.concurrent.TimeUnit;

public class DefaultDataBaseObservation implements DataBaseObservation {
    protected final DatabaseTelemetryConfig config;
    protected final String poolName;
    protected final QueryContext query;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> timer;
    protected final Logger log;
    protected final long start = System.nanoTime();
    protected Throwable error;
    protected long statement = start;

    public DefaultDataBaseObservation(DatabaseTelemetryConfig config, String poolName, QueryContext query, Span span, Meter.MeterProvider<Timer> timer, Logger log) {
        this.config = config;
        this.poolName = poolName;
        this.query = query;
        this.span = span;
        this.timer = timer;
        this.log = log;
    }

    @Override
    public void observeConnection() {
        this.span.addEvent("connection");
    }

    @Override
    public void observeStatement() {
        this.statement = System.nanoTime();
        this.span.addEvent("statement");
        if (log.isDebugEnabled()) {
            log.atDebug()
                .addKeyValue("sqlQuery", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("pool", this.poolName);
                    gen.writeStringProperty("operation", query.operation());
                    gen.writeStringProperty("queryId", query.queryId());
                    if (log.isTraceEnabled()) {
                        gen.writeStringProperty("sql", query.sql());
                    }
                    gen.writeEndObject();
                }))
                .log("Executing query");
        }
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
        var took = System.nanoTime() - this.statement;
        this.span.addEvent("result");
        this.timer.withTag(ErrorAttributes.ERROR_TYPE.getKey(), error == null ? "" : error.getClass().getCanonicalName())
            .record(took, TimeUnit.NANOSECONDS);
        if (log.isDebugEnabled()) {
            log.atDebug()
                .addKeyValue("sqlQuery", StructuredArgument.value(gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("pool", this.poolName);
                    gen.writeStringProperty("operation", query.operation());
                    gen.writeStringProperty("queryId", query.queryId());
                    gen.writeNumberProperty("processingTime", took / 1_000_000);
                    if (log.isTraceEnabled()) {
                        gen.writeStringProperty("sql", query.sql());
                    }
                    gen.writeEndObject();
                }))
                .log("Query executed");
        }
        if (error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }
}

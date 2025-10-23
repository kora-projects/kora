package ru.tinkoff.kora.database.common.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import ru.tinkoff.kora.database.common.QueryContext;

import java.util.concurrent.ConcurrentHashMap;

public class DefaultDataBaseTelemetry implements DataBaseTelemetry {
    private static final Meter.Id emptyCounterId = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.TIMER);
    private final DatabaseTelemetryConfig config;
    private final String poolName;
    private final String dbSystem;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final Logger log;
    protected ConcurrentHashMap<QueryContext, ConcurrentHashMap<Tags, Timer>> timers = new ConcurrentHashMap<>();

    public DefaultDataBaseTelemetry(DatabaseTelemetryConfig config, String poolName, String dbSystem, Tracer tracer, MeterRegistry meterRegistry, Logger log) {
        this.config = config;
        this.poolName = poolName;
        this.dbSystem = dbSystem;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.log = log;
    }

    @Override
    public DataBaseObservation observe(QueryContext query) {
        var span = this.createSpan(query);
        var timer = this.createTimer(query);
        var log = this.config.logging().enabled() ? this.log : NOPLogger.NOP_LOGGER;

        return new DefaultDataBaseObservation(
            this.config,
            this.poolName,
            query,
            span,
            timer,
            log
        );
    }

    protected Span createSpan(QueryContext query) {
        if (!config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var builder = this.tracer.spanBuilder(query.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, this.dbSystem)
            .setAttribute(DbAttributes.DB_QUERY_TEXT, query.queryId());
        for (var entry : config.tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }
        return builder.startSpan();
    }

    protected Meter.MeterProvider<Timer> createTimer(QueryContext query) {
        if (!this.config.metrics().enabled()) {
            return _ -> new NoopTimer(emptyCounterId);
        }

        var map = timers.computeIfAbsent(query, k -> new ConcurrentHashMap<>());
        return tags -> map.computeIfAbsent(Tags.of(tags), t -> {
                var b = Timer.builder("db.client.operation.duration")
                    .serviceLevelObjectives(this.config.metrics().slo())
                    .tag(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), this.poolName)
                    .tag(DbAttributes.DB_QUERY_TEXT.getKey(), query.queryId())
                    .tag(DbAttributes.DB_OPERATION_NAME.getKey(), query.operation())
                    .tags(t);
                for (var e : this.config.metrics().tags().entrySet()) {
                    b.tag(e.getKey(), e.getValue());
                }
                return b.register(this.meterRegistry);
            }
        );
    }
}

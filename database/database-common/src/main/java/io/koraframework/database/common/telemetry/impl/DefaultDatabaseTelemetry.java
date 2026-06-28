package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.DbAttributes;

public class DefaultDatabaseTelemetry implements DatabaseTelemetry {

    public record TelemetryContext(DatabaseTelemetryConfig config,
                                   String poolName,
                                   String dbSystem,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $DatabaseTelemetryConfig_ConfigValueExtractor.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueExtractor.DatabaseLoggingConfig_Defaults(),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor.DatabaseMetricsConfig_Defaults(),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor.DatabaseTracingConfig_Defaults()
            ),
            "none",
            "none",
            false,
            false,
            DefaultDatabaseTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultDatabaseTelemetryFactory.NOOP_TRACER
        );
    }

    protected final TelemetryContext context;
    protected final DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger;
    protected final DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics;

    public DefaultDatabaseTelemetry(DatabaseTelemetryConfig config,
                                    String poolName,
                                    String dbSystem,
                                    Tracer tracer,
                                    MeterRegistry meterRegistry,
                                    DefaultDatabaseMetricsFactory metricsFactory,
                                    DefaultDatabaseLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultDatabaseTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultDatabaseTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, poolName, dbSystem, isTracingEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.context.meterRegistry();
    }

    @Override
    public DatabaseObservation observe(QueryContext query) {
        var span = context.isTracingEnabled
            ? this.createSpan(query).startSpan()
            : Span.getInvalid();

        return new DefaultDatabaseObservation(query, context, logger, metrics, span);
    }

    protected SpanBuilder createSpan(QueryContext query) {
        var builder = this.context.tracer().spanBuilder(query.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, this.context.dbSystem())
            .setAttribute(DbAttributes.DB_QUERY_TEXT, query.queryId());

        for (var entry : context.config().tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }
        return builder;
    }
}

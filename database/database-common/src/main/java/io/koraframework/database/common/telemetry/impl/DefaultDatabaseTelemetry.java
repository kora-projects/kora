package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.DbAttributes;
import java.util.Map;

public class DefaultDatabaseTelemetry implements DatabaseTelemetry {

    public record TelemetryContext(DatabaseTelemetryConfig config,
                                   String poolName,
                                   String dbSystem,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Map.Entry<String, String>[] staticAttributes) {

        @SuppressWarnings("unchecked")
        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Defaults(),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Defaults(),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Defaults()
            ),
            "none",
            "none",
            false,
            false,
            DefaultDatabaseTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultDatabaseTelemetryFactory.NOOP_TRACER,
            new Map.Entry[0]
        );
    }

    protected final TelemetryContext context;
    protected final DefaultDatabaseLoggerFactory.DefaultDatabaseLogger logger;
    protected final DefaultDatabaseMetricsFactory.DefaultDatabaseMetrics metrics;

    @SuppressWarnings("unchecked")
    public DefaultDatabaseTelemetry(DatabaseTelemetryConfig config,
                                    String poolName,
                                    String dbSystem,
                                    Tracer tracer,
                                    MeterRegistry meterRegistry,
                                    DefaultDatabaseMetricsFactory metricsFactory,
                                    DefaultDatabaseLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultDatabaseTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultDatabaseTelemetryFactory.NOOP_METER_REGISTRY;

        Map.Entry<String, String>[] staticAttrs;
        if (isTracingEnabled && config.tracing().attributes() != null) {
            staticAttrs = config.tracing().attributes().entrySet().toArray(new Map.Entry[0]);
        } else {
            staticAttrs = new Map.Entry[0];
        }

        this.context = new TelemetryContext(config, poolName, dbSystem, isTracingEnabled, isMetricsEnabled, meterRegistry, tracer, staticAttrs);
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

        return DefaultDatabaseObservation.recycle(query, context, logger, metrics, span);
    }

    protected SpanBuilder createSpan(QueryContext query) {
        var builder = this.context.tracer().spanBuilder(query.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, this.context.dbSystem())
            .setAttribute(DbAttributes.DB_QUERY_TEXT, query.queryId());

        final Map.Entry<String, String>[] attrs = context.staticAttributes();
        for (int i = 0; i < attrs.length; i++) {
            var entry = attrs[i];
            builder.setAttribute(entry.getKey(), entry.getValue());
        }
        return builder;
    }
}

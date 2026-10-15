package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import io.koraframework.database.jdbc.telemetry.JdbcTransactionObservation;
import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;

public class DefaultJdbcDatabaseTelemetry implements JdbcDatabaseTelemetry {

    public static final String DB_TRANSACTION_ISOLATION_LEVEL = "db.transaction.isolation_level";

    public record TelemetryContext(DatabaseTelemetryConfig config,
                                   String poolName,
                                   String dbSystem,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

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
            DefaultJdbcDatabaseTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultJdbcDatabaseTelemetryFactory.NOOP_TRACER
        );
    }

    protected final TelemetryContext context;
    protected final DefaultJdbcDatabaseLoggerFactory.DefaultJdbcDatabaseLogger logger;
    protected final DefaultJdbcDatabaseMetricsFactory.DefaultJdbcDatabaseMetrics metrics;

    public DefaultJdbcDatabaseTelemetry(DatabaseTelemetryConfig config,
                                           String poolName,
                                           String dbSystem,
                                           Tracer tracer,
                                           MeterRegistry meterRegistry,
                                           DefaultJdbcDatabaseMetricsFactory metricsFactory,
                                           DefaultJdbcDatabaseLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultJdbcDatabaseTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultJdbcDatabaseTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, poolName, dbSystem, isTracingEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public JdbcTransactionObservation observeTransaction(JdbcTransactionContext transaction) {
        var span = context.isTracingEnabled
            ? this.createTransactionSpan(transaction).startSpan()
            : Span.getInvalid();

        return new DefaultJdbcTransactionObservation(transaction, context, logger, metrics, span);
    }

    protected SpanBuilder createTransactionSpan(JdbcTransactionContext transaction) {
        var builder = this.context.tracer().spanBuilder("db_transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME, this.context.dbSystem())
            .setAttribute(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME, this.context.poolName())
            .setAttribute(DB_TRANSACTION_ISOLATION_LEVEL, transaction.isolationLevel());

        for (var entry : context.config().tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }
        return builder;
    }
}

package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetry;
import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public class DefaultJdbcDatabaseTelemetryFactory implements JdbcDatabaseTelemetryFactory {

    public static final Tracer NOOP_TRACER = DefaultDatabaseTelemetryFactory.NOOP_TRACER;
    public static final MeterRegistry NOOP_METER_REGISTRY = DefaultDatabaseTelemetryFactory.NOOP_METER_REGISTRY;

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultJdbcDatabaseLoggerFactory loggerFactory;
    @Nullable
    private final DefaultJdbcDatabaseMetricsFactory metricsFactory;

    public DefaultJdbcDatabaseTelemetryFactory(@Nullable Tracer tracer,
                                                  @Nullable MeterRegistry meterRegistry,
                                                  @Nullable DefaultJdbcDatabaseLoggerFactory loggerFactory,
                                                  @Nullable DefaultJdbcDatabaseMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public JdbcDatabaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopJdbcDatabaseTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultJdbcDatabaseMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultJdbcDatabaseMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopJdbcDatabaseMetricsFactory.INSTANCE;
        }

        final DefaultJdbcDatabaseLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultJdbcDatabaseLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopJdbcDatabaseLoggerFactory.INSTANCE;
        }

        return build(name, dbType, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected JdbcDatabaseTelemetry build(String name,
                                             String dbType,
                                             DatabaseTelemetryConfig config,
                                             Tracer tracer,
                                             MeterRegistry meterRegistry,
                                             DefaultJdbcDatabaseMetricsFactory metricsFactory,
                                             DefaultJdbcDatabaseLoggerFactory loggerFactory) {
        return new DefaultJdbcDatabaseTelemetry(config, name, dbType, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}

package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultDatabaseTelemetryFactory implements DatabaseTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("database");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultDatabaseLoggerFactory loggerFactory;
    @Nullable
    private final DefaultDatabaseMetricsFactory metricsFactory;

    public DefaultDatabaseTelemetryFactory(@Nullable Tracer tracer,
                                           @Nullable MeterRegistry meterRegistry,
                                           @Nullable DefaultDatabaseLoggerFactory loggerFactory,
                                           @Nullable DefaultDatabaseMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public DatabaseTelemetry get(DatabaseTelemetryConfig config, String name, String dbType) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopDatabaseTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;

        final DefaultDatabaseMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultDatabaseMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopDatabaseMetricsFactory.INSTANCE;
        }

        final DefaultDatabaseLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultDatabaseLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopDatabaseLoggerFactory.INSTANCE;
        }

        return build(name, dbType, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory);
    }

    protected DatabaseTelemetry build(String name,
                                      String dbType,
                                      DatabaseTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultDatabaseMetricsFactory metricsFactory,
                                      DefaultDatabaseLoggerFactory loggerFactory) {
        return new DefaultDatabaseTelemetry(config, name, dbType, tracer, meterRegistry, metricsFactory, loggerFactory);
    }
}

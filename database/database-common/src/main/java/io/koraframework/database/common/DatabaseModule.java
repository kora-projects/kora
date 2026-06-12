package io.koraframework.database.common;

import io.koraframework.common.DefaultComponent;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseMetricsFactory;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface DatabaseModule {

    @DefaultComponent
    default DefaultDatabaseTelemetryFactory defaultDatabaseTelemetry(@Nullable Tracer tracer,
                                                                     @Nullable MeterRegistry meterRegistry,
                                                                     @Nullable DefaultDatabaseLoggerFactory loggerFactory,
                                                                     @Nullable DefaultDatabaseMetricsFactory metricsFactory) {
        return new DefaultDatabaseTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}

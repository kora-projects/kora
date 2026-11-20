package io.koraframework.database.jdbc;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.database.jdbc.telemetry.JdbcDatabaseTelemetryFactory;
import io.koraframework.database.jdbc.telemetry.impl.DefaultJdbcDatabaseLoggerFactory;
import io.koraframework.database.jdbc.telemetry.impl.DefaultJdbcDatabaseMetricsFactory;
import io.koraframework.database.jdbc.telemetry.impl.DefaultJdbcDatabaseTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface JdbcDatabaseModule extends JdbcMapperModule {

    @FactoryModule
    default JdbcDatabaseFactoryModule jdbcDatabase() {
        return new JdbcDatabaseFactoryModule("jdbc");
    }

    @DefaultComponent
    default JdbcDatabaseTelemetryFactory defaultJdbcDatabaseTelemetryFactory(@Nullable Tracer tracer,
                                                                             @Nullable MeterRegistry meterRegistry,
                                                                             @Nullable DefaultJdbcDatabaseLoggerFactory loggerFactory,
                                                                             @Nullable DefaultJdbcDatabaseMetricsFactory metricsFactory) {
        return new DefaultJdbcDatabaseTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}

package ru.tinkoff.kora.database.common;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.database.common.telemetry.DataBaseLoggerFactory;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriterFactory;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTracerFactory;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;

public interface DataBaseModule {

    @Nonnull
    @DefaultComponent
    default DefaultDataBaseTelemetryFactory defaultDataBaseTelemetry(@Nullable DataBaseLoggerFactory loggerFactory,
                                                                     @Nullable DataBaseMetricWriterFactory metricWriterFactory,
                                                                     @Nullable DataBaseTracerFactory tracingFactory) {
        return new DefaultDataBaseTelemetryFactory(loggerFactory, metricWriterFactory, tracingFactory);
    }

    @DefaultComponent
    default DataBaseLoggerFactory.DefaultDataBaseLoggerFactory defaultDataBaseLoggerFactory() {
        return new DataBaseLoggerFactory.DefaultDataBaseLoggerFactory();
    }
}

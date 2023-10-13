package ru.tinkoff.kora.database.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class DefaultDataBaseTelemetryFactory implements DataBaseTelemetryFactory {
    @Nullable
    private final DataBaseLoggerFactory loggerFactory;
    @Nullable
    private final DataBaseMetricWriterFactory metricWriterFactory;
    @Nullable
    private final DataBaseTracerFactory tracingFactory;

    public DefaultDataBaseTelemetryFactory(@Nullable DataBaseLoggerFactory loggerFactory, @Nullable DataBaseMetricWriterFactory metricWriterFactory, @Nullable DataBaseTracerFactory tracingFactory) {
        this.loggerFactory = loggerFactory;
        this.metricWriterFactory = metricWriterFactory;
        this.tracingFactory = tracingFactory;
    }

    @Override
    public DataBaseTelemetry get(TelemetryConfig config, String name, String driverType, String dbType, String username) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), name);
        var metricWriter = this.metricWriterFactory == null ? null : this.metricWriterFactory.get(config.metrics(), name);
        var tracingFactory = this.tracingFactory == null ? null : this.tracingFactory.get(config.tracing(), dbType, null, username);
        if (logger == null && metricWriter == null && tracingFactory == null) {
            return EMPTY;
        }

        return new DefaultDataBaseTelemetry(metricWriter, tracingFactory, logger);
    }
}

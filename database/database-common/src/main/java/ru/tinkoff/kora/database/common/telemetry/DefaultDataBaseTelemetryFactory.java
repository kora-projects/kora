package ru.tinkoff.kora.database.common.telemetry;

import javax.annotation.Nullable;

public class DefaultDataBaseTelemetryFactory implements DataBaseTelemetryFactory {
    @Nullable
    private final DataBaseLoggerFactory loggerFactory;
    @Nullable
    private final DataBaseMetricWriterFactory metricWriterFactory;
    @Nullable
    private final DataBaseTracerFactory tracingFactory;

    public DefaultDataBaseTelemetryFactory(
        @Nullable DataBaseLoggerFactory loggerFactory, @Nullable DataBaseMetricWriterFactory metricWriterFactory, @Nullable DataBaseTracerFactory tracingFactory) {
        this.loggerFactory = loggerFactory;
        this.metricWriterFactory = metricWriterFactory;
        this.tracingFactory = tracingFactory;
    }

    @Override
    public DataBaseTelemetry get(String name, String dbType, String username) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(name);
        var metricWriter = this.metricWriterFactory == null ? null : this.metricWriterFactory.get(name);
        var tracingFactory = this.tracingFactory == null ? null : this.tracingFactory.get(dbType, null, username);

        return new DefaultDataBaseTelemetry(metricWriter, tracingFactory, logger);
    }
}

package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriterFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.DbMetricsConfig;

import javax.annotation.Nullable;

public final class MicrometerDataBaseMetricWriterFactory implements DataBaseMetricWriterFactory {
    private final MeterRegistry meterRegistry;
    private final DbMetricsConfig config;

    public MicrometerDataBaseMetricWriterFactory(MeterRegistry meterRegistry, @Nullable DbMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public DataBaseMetricWriter get(String poolName) {
        return new MicrometerDataBaseMetricWriter(this.meterRegistry, this.config, poolName);
    }
}

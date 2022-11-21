package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriterFactory;

public final class MicrometerDataBaseMetricWriterFactory implements DataBaseMetricWriterFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerDataBaseMetricWriterFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public DataBaseMetricWriter get(String poolName) {
        return new MicrometerDataBaseMetricWriter(this.meterRegistry, poolName);
    }
}

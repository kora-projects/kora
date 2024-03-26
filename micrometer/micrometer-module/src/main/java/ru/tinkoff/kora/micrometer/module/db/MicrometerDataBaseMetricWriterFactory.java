package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriterFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerDataBaseMetricWriterFactory implements DataBaseMetricWriterFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerDataBaseMetricWriterFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public DataBaseMetricWriter get(TelemetryConfig.MetricsConfig metrics, String poolName) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return switch (metrics.spec()) {
                case V120 -> new Opentelemetry120DataBaseMetricWriter(this.meterRegistry, metrics, poolName);
                case V123 -> new Opentelemetry123DataBaseMetricWriter(this.meterRegistry, metrics, poolName);
            };
        } else {
            return null;
        }
    }
}

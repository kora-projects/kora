package ru.tinkoff.kora.micrometer.module.db;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriter;
import ru.tinkoff.kora.database.common.telemetry.DataBaseMetricWriterFactory;
import ru.tinkoff.kora.database.common.telemetry.DatabaseMetricsConfig;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Map;
import java.util.Objects;

public final class MicrometerDataBaseMetricWriterFactory implements DataBaseMetricWriterFactory {
    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerDataBaseMetricWriterFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Override
    @Nullable
    public DataBaseMetricWriter get(TelemetryConfig.MetricsConfig config, String poolName) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            Map<String, String> tags = (config instanceof DatabaseMetricsConfig db)
                ? db.tags()
                : Map.of();
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120DataBaseMetricWriter(this.meterRegistry, config, poolName, tags);
                case V123 -> new Opentelemetry123DataBaseMetricWriter(this.meterRegistry, config, poolName, tags);
            };
        } else {
            return null;
        }
    }
}

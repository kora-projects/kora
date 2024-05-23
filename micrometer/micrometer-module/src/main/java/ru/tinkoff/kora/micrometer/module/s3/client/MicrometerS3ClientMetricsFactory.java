package ru.tinkoff.kora.micrometer.module.s3.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientMetrics;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientMetricsFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class MicrometerS3ClientMetricsFactory implements S3ClientMetricsFactory {

    private final MetricsConfig metricsConfig;
    private final MeterRegistry meterRegistry;

    public MicrometerS3ClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.metricsConfig = metricsConfig;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public S3ClientMetrics get(TelemetryConfig.MetricsConfig config, String clientName) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (this.metricsConfig.opentelemetrySpec()) {
                case V120 -> new Micrometer120S3ClientMetrics(this.meterRegistry, config, clientName);
                case V123 -> new Micrometer123S3ClientMetrics(this.meterRegistry, config, clientName);
            };
        } else {
            return null;
        }
    }
}

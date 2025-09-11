package ru.tinkoff.kora.micrometer.module.s3.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientMetrics;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class MicrometerS3ClientMetricsFactory implements S3ClientMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerS3ClientMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public S3ClientMetrics get(TelemetryConfig.MetricsConfig config, Class<?> client) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryS3ClientMetrics(this.meterRegistry, config, client);
        } else {
            return null;
        }
    }
}

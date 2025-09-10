package ru.tinkoff.kora.micrometer.module.s3.client;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientMetrics;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class MicrometerS3KoraClientMetricsFactory implements S3KoraClientMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerS3KoraClientMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public S3KoraClientMetrics get(TelemetryConfig.MetricsConfig config, Class<?> client) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryS3KoraClientMetrics(this.meterRegistry, config, client);
        } else {
            return null;
        }
    }
}

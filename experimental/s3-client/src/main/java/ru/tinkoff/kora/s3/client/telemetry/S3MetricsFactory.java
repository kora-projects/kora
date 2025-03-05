package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3MetricsFactory {

    @Nullable
    S3Metrics get(TelemetryConfig.MetricsConfig metrics, Class<?> clientImpl);
}

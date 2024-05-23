package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3ClientMetricsFactory {

    @Nullable
    S3ClientMetrics get(TelemetryConfig.MetricsConfig metrics, String clientName);
}

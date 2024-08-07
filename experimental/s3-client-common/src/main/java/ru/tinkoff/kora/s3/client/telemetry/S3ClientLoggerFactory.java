package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3ClientLoggerFactory {

    @Nullable
    S3ClientLogger get(TelemetryConfig.LogConfig logging, String clientName);
}

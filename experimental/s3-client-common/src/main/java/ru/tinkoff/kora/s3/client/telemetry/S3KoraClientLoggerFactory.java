package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3KoraClientLoggerFactory {

    @Nullable
    S3KoraClientLogger get(TelemetryConfig.LogConfig logging, Class<?> clientImpl);
}

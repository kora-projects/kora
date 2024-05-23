package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3ClientTracerFactory {

    @Nullable
    S3ClientTracer get(TelemetryConfig.TracingConfig tracing, String clientName);
}

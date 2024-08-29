package ru.tinkoff.kora.s3.client.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3KoraClientTracerFactory {

    @Nullable
    S3KoraClientTracer get(TelemetryConfig.TracingConfig tracing, Class<?> clientImpl);
}

package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface HttpServerTracerFactory {
    @Nullable
    HttpServerTracer get(TelemetryConfig.TracingConfig tracing);
}

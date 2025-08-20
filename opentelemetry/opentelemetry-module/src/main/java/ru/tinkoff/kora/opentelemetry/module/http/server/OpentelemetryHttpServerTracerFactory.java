package ru.tinkoff.kora.opentelemetry.module.http.server;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryHttpServerTracerFactory implements HttpServerTracerFactory {
    private final Tracer tracer;

    public OpentelemetryHttpServerTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public HttpServerTracer get(TelemetryConfig.TracingConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryHttpServerTracer(this.tracer);
        } else {
            return null;
        }
    }
}

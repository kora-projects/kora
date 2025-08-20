package ru.tinkoff.kora.opentelemetry.module.http.server;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryHttpServerTracerFactory implements HttpServerTracerFactory {

    private final Tracer tracer;

    public OpentelemetryHttpServerTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Deprecated
    @Nullable
    @Override
    public HttpServerTracer get(TelemetryConfig.TracingConfig tracing) {
        return this.get(tracing, null);
    }

    @Nullable
    @Override
    public HttpServerTracer get(TelemetryConfig.TracingConfig tracingConfig, @Nullable HttpServerConfig serverConfig) {
        if (Objects.requireNonNullElse(tracingConfig.enabled(), true)) {
            return new OpentelemetryHttpServerTracer(this.tracer, serverConfig);
        } else {
            return null;
        }
    }
}

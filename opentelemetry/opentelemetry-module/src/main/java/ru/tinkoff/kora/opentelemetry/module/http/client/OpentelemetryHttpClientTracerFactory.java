package ru.tinkoff.kora.opentelemetry.module.http.client;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTracer;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryHttpClientTracerFactory implements HttpClientTracerFactory {
    private final Tracer tracer;

    public OpentelemetryHttpClientTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    @Nullable
    public HttpClientTracer get(TelemetryConfig.TracingConfig config, String clientName) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryHttpClientTracer(this.tracer);
        } else {
            return null;
        }
    }
}

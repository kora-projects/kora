package ru.tinkoff.kora.opentelemetry.module.http.client;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTracer;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTracerFactory;

public final class OpentelemetryHttpClientTracerFactory implements HttpClientTracerFactory {
    private final Tracer tracer;

    public OpentelemetryHttpClientTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public HttpClientTracer get(String clientName) {
        return new OpentelemetryHttpClientTracer(this.tracer);
    }
}

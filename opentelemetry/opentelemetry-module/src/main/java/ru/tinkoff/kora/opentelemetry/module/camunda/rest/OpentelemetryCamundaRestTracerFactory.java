package ru.tinkoff.kora.opentelemetry.module.camunda.rest;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracer;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryCamundaRestTracerFactory implements CamundaRestTracerFactory {

    private final Tracer tracer;

    public OpentelemetryCamundaRestTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public CamundaRestTracer get(TelemetryConfig.TracingConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryCamundaRestTracer(this.tracer);
        } else {
            return null;
        }
    }
}

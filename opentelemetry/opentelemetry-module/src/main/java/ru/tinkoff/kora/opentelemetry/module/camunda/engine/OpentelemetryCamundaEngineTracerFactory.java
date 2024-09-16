package ru.tinkoff.kora.opentelemetry.module.camunda.engine;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.telemetry.CamundaEngineTracer;
import ru.tinkoff.kora.camunda.engine.telemetry.CamundaEngineTracerFactory;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracer;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryCamundaEngineTracerFactory implements CamundaEngineTracerFactory {

    private final Tracer tracer;

    public OpentelemetryCamundaEngineTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public CamundaEngineTracer get(TelemetryConfig.TracingConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryCamundaEngineTracer(this.tracer);
        } else {
            return null;
        }
    }
}

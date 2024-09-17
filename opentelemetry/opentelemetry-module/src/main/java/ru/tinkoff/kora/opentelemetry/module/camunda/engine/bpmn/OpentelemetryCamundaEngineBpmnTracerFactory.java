package ru.tinkoff.kora.opentelemetry.module.camunda.engine.bpmn;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTracer;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryCamundaEngineBpmnTracerFactory implements CamundaEngineBpmnTracerFactory {

    private final Tracer tracer;

    public OpentelemetryCamundaEngineBpmnTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public CamundaEngineBpmnTracer get(TelemetryConfig.TracingConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryCamundaEngineBpmnTracer(this.tracer);
        } else {
            return null;
        }
    }
}

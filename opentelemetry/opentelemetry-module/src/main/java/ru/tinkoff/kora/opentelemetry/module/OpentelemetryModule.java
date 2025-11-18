package ru.tinkoff.kora.opentelemetry.module;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.opentelemetry.module.camunda.engine.bpmn.OpentelemetryCamundaEngineBpmnTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.camunda.zeebe.worker.OpentelemetryZeebeWorkerTracerFactory;

public interface OpentelemetryModule {

    @DefaultComponent
    default OpentelemetryCamundaEngineBpmnTracerFactory opentelemetryCamundaEngineBpmnTracerFactory(Tracer tracer) {
        return new OpentelemetryCamundaEngineBpmnTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryZeebeWorkerTracerFactory opentelemetryZeebeWorkerTracerFactory(Tracer tracer) {
        return new OpentelemetryZeebeWorkerTracerFactory(tracer);
    }
}

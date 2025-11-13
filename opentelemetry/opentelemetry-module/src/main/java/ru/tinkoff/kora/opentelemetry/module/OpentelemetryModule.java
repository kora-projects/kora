package ru.tinkoff.kora.opentelemetry.module;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.opentelemetry.module.camunda.engine.bpmn.OpentelemetryCamundaEngineBpmnTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.camunda.rest.OpentelemetryCamundaRestTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.camunda.zeebe.worker.OpentelemetryZeebeWorkerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.jms.consumer.OpentelemetryJmsConsumerTracer;
import ru.tinkoff.kora.opentelemetry.module.scheduling.OpentelemetrySchedulingTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.soap.client.OpentelemetrySoapClientTracerFactory;

public interface OpentelemetryModule {

    @DefaultComponent
    default OpentelemetrySoapClientTracerFactory opentelemetrySoapClientTracingFactory(Tracer tracer) {
        return new OpentelemetrySoapClientTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryJmsConsumerTracer opentelemetryJmsConsumerTracing(Tracer tracer) {
        return new OpentelemetryJmsConsumerTracer(tracer);
    }

    @DefaultComponent
    default OpentelemetrySchedulingTracerFactory opentelemetrySchedulingTracerFactory(Tracer tracer) {
        return new OpentelemetrySchedulingTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryCamundaEngineBpmnTracerFactory opentelemetryCamundaEngineBpmnTracerFactory(Tracer tracer) {
        return new OpentelemetryCamundaEngineBpmnTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryCamundaRestTracerFactory opentelemetryCamundaRestTracerFactory(Tracer tracer) {
        return new OpentelemetryCamundaRestTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryZeebeWorkerTracerFactory opentelemetryZeebeWorkerTracerFactory(Tracer tracer) {
        return new OpentelemetryZeebeWorkerTracerFactory(tracer);
    }
}

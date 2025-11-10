package ru.tinkoff.kora.opentelemetry.module;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.opentelemetry.module.cache.OpentelementryCacheTracer;
import ru.tinkoff.kora.opentelemetry.module.camunda.engine.bpmn.OpentelemetryCamundaEngineBpmnTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.camunda.rest.OpentelemetryCamundaRestTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.camunda.zeebe.worker.OpentelemetryZeebeWorkerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.http.server.OpentelemetryHttpServerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.jms.consumer.OpentelemetryJmsConsumerTracer;
import ru.tinkoff.kora.opentelemetry.module.s3.client.OpentelemetryS3ClientTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.s3.client.OpentelemetryS3KoraClientTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.scheduling.OpentelemetrySchedulingTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.soap.client.OpentelemetrySoapClientTracerFactory;

public interface OpentelemetryModule {

    @DefaultComponent
    default OpentelemetryHttpServerTracerFactory opentelemetryHttpServerTracerFactory(Tracer tracer) {
        return new OpentelemetryHttpServerTracerFactory(tracer);
    }

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
    default OpentelementryCacheTracer opentelemetryCacheTracer(Tracer tracer) {
        return new OpentelementryCacheTracer(tracer);
    }

    @DefaultComponent
    default OpentelemetryS3ClientTracerFactory opentelemetryS3ClientTracerFactory(Tracer tracer) {
        return new OpentelemetryS3ClientTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryS3KoraClientTracerFactory opentelemetryS3KoraClientTracerFactory(Tracer tracer) {
        return new OpentelemetryS3KoraClientTracerFactory(tracer);
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

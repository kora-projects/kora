package ru.tinkoff.kora.opentelemetry.module;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.opentelemetry.module.cache.OpentelementryCacheTracer;
import ru.tinkoff.kora.opentelemetry.module.camunda.zeebe.worker.OpentelemetryZeebeWorkerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.db.OpentelemetryDataBaseTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.grpc.client.OpentelemetryGrpcClientTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.grpc.server.OpentelemetryGrpcServerTracer;
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.http.server.OpentelemetryHttpServerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.jms.consumer.OpentelemetryJmsConsumerTracer;
import ru.tinkoff.kora.opentelemetry.module.kafka.consumer.OpentelemetryKafkaConsumerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.kafka.consumer.OpentelemetryKafkaProducerTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.scheduling.OpentelemetrySchedulingTracerFactory;

public interface OpentelemetryModule {
    @DefaultComponent
    default OpentelemetryHttpServerTracerFactory opentelemetryHttpServerTracerFactory(Tracer tracer) {
        return new OpentelemetryHttpServerTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryHttpClientTracerFactory opentelemetryHttpClientTracingFactory(Tracer tracer) {
        return new OpentelemetryHttpClientTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryGrpcClientTracerFactory opentelemetryGrpcClientTracerFactory(Tracer tracer) {
        return new OpentelemetryGrpcClientTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryGrpcServerTracer opentelemetryGrpcServerTracing(Tracer tracer) {
        return new OpentelemetryGrpcServerTracer(tracer);
    }

    @DefaultComponent
    default OpentelemetryDataBaseTracerFactory opentelemetryDataBaseTracingFactory(Tracer tracer) {
        return new OpentelemetryDataBaseTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryKafkaConsumerTracerFactory opentelemetryKafkaConsumerTracerFactory(Tracer tracer) {
        return new OpentelemetryKafkaConsumerTracerFactory(tracer);
    }

    @DefaultComponent
    default OpentelemetryKafkaProducerTracerFactory opentelemetryKafkaProducerTracerFactory(Tracer tracer) {
        return new OpentelemetryKafkaProducerTracerFactory(tracer);
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
    default OpentelemetryZeebeWorkerTracerFactory opentelemetryZeebeWorkerTracerFactory(Tracer tracer) {
        return new OpentelemetryZeebeWorkerTracerFactory(tracer);
    }
}

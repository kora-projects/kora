package ru.tinkoff.kora.opentelemetry.module;

import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.opentelemetry.module.cache.OpentelementryCacheTracer;
import ru.tinkoff.kora.opentelemetry.module.db.OpentelemetryDataBaseTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.grpc.server.OpentelemetryGrpcServerTracer;
import ru.tinkoff.kora.opentelemetry.module.http.client.OpentelemetryHttpClientTracerFactory;
import ru.tinkoff.kora.opentelemetry.module.http.server.OpentelemetryHttpServerTracer;
import ru.tinkoff.kora.opentelemetry.module.jms.consumer.OpentelemetryJmsConsumerTracer;
import ru.tinkoff.kora.opentelemetry.module.kafka.consumer.OpentelemetryKafkaConsumerTracer;
import ru.tinkoff.kora.opentelemetry.module.kafka.consumer.OpentelemetryKafkaProducerTracer;
import ru.tinkoff.kora.opentelemetry.module.scheduling.OpentelemetrySchedulingTracerFactory;

public interface OpentelemetryModule {
    @DefaultComponent
    default OpentelemetryHttpServerTracer opentelemetryHttpServerTracer(Tracer tracer) {
        return new OpentelemetryHttpServerTracer(tracer);
    }

    @DefaultComponent
    default OpentelemetryHttpClientTracerFactory opentelemetryHttpClientTracingFactory(Tracer tracer) {
        return new OpentelemetryHttpClientTracerFactory(tracer);
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
    default OpentelemetryKafkaConsumerTracer opentelemetryKafkaConsumerTracing(Tracer tracer) {
        return new OpentelemetryKafkaConsumerTracer(tracer);
    }

    @DefaultComponent
    default OpentelemetryKafkaProducerTracer opentelemetryKafkaProducerTracer(Tracer tracer) {
        return new OpentelemetryKafkaProducerTracer(tracer);
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
}

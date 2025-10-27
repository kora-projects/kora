package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.contrib.metrics.micrometer.CallbackRegistrar;
import io.opentelemetry.contrib.metrics.micrometer.MicrometerMeterProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.CommandLatencyRecorderFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.micrometer.module.cache.MicrometerCacheMetrics;
import ru.tinkoff.kora.micrometer.module.cache.caffeine.MicrometerCaffeineCacheMetricCollector;
import ru.tinkoff.kora.micrometer.module.cache.redis.lettuce.MicrometerLettuceCommandLatencyRecorderFactory;
import ru.tinkoff.kora.micrometer.module.camunda.engine.bpmn.MicrometerCamundaEngineBpmnMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.rest.MicrometerCamundaRestMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.zeebe.job.MicrometerZeebeClientWorkerJobMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker.MicrometerZeebeWorkerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.grpc.client.MicrometerGrpcClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.DefaultMicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.grpc.server.MicrometerGrpcServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.grpc.server.tag.DefaultMicrometerGrpcServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.grpc.server.tag.MicrometerGrpcServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.client.MicrometerHttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.client.tag.MicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.client.tag.OpentelemetryMicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerHttpServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerPrivateApiMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DefaultMicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.jms.consumer.MicrometerJmsConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerCircuitBreakerMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerFallbackMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerRetryMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerTimeoutMetrics;
import ru.tinkoff.kora.micrometer.module.s3.client.MicrometerS3ClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.s3.client.MicrometerS3KoraClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.scheduling.MicrometerSchedulingMetricsFactory;
import ru.tinkoff.kora.micrometer.module.soap.client.MicrometerSoapClientMetricsFactory;

public interface MetricsModule {
    @Root
    default PrometheusMeterRegistryWrapper prometheusMeterRegistry(All<PrometheusMeterRegistryInitializer> initializers) {
        return new PrometheusMeterRegistryWrapper(initializers);
    }

    @DefaultComponent
    default MicrometerHttpServerTagsProvider micrometerHttpServerTagsProvider(HttpServerConfig config) {
        return new DefaultMicrometerHttpServerTagsProvider();
    }

    @DefaultComponent
    default MicrometerHttpServerMetricsFactory micrometerHttpServerMetricsFactory(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider) {
        return new MicrometerHttpServerMetricsFactory(meterRegistry, httpServerTagsProvider);
    }

    @DefaultComponent
    default MicrometerHttpClientTagsProvider micrometerHttpClientTagsProvider(HttpClientConfig httpClientConfig) {
        return new OpentelemetryMicrometerHttpClientTagsProvider();
    }

    @DefaultComponent
    default MicrometerHttpClientMetricsFactory micrometerHttpClientMetricsFactory(MeterRegistry meterRegistry,
                                                                                  MicrometerHttpClientTagsProvider tagsProvider) {
        return new MicrometerHttpClientMetricsFactory(meterRegistry, tagsProvider);
    }

    @DefaultComponent
    default MicrometerSoapClientMetricsFactory micrometerSoapClientMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerSoapClientMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerPrivateApiMetrics micrometerPrivateApiMetrics(PrometheusMeterRegistry meterRegistry) {
        return new MicrometerPrivateApiMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerGrpcServerTagsProvider micrometerGrpcServerTagsProvider() {
        return new DefaultMicrometerGrpcServerTagsProvider();
    }

    @DefaultComponent
    default MicrometerGrpcServerMetricsFactory micrometerGrpcServerMetricsFactory(MeterRegistry meterRegistry, MicrometerGrpcServerTagsProvider grpcServerTagsProvider) {
        return new MicrometerGrpcServerMetricsFactory(meterRegistry, grpcServerTagsProvider);
    }

    @DefaultComponent
    default MicrometerGrpcClientTagsProvider micrometerGrpcClientTagsProvider() {
        return new DefaultMicrometerGrpcClientTagsProvider();
    }

    @DefaultComponent
    default MicrometerGrpcClientMetricsFactory micrometerGrpcClientMetricsFactory(MeterRegistry registry,
                                                                                  MicrometerGrpcClientTagsProvider tagsProvider) {
        return new MicrometerGrpcClientMetricsFactory(registry, tagsProvider);
    }

    @DefaultComponent
    default MicrometerJmsConsumerMetricsFactory micrometerJmsConsumerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerJmsConsumerMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerSchedulingMetricsFactory micrometerSchedulingMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerSchedulingMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerCircuitBreakerMetrics micrometerCircuitBreakerMetrics(MeterRegistry meterRegistry) {
        return new MicrometerCircuitBreakerMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerFallbackMetrics micrometerFallbackMetrics(MeterRegistry meterRegistry) {
        return new MicrometerFallbackMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerRetryMetrics micrometerRetryMetrics(MeterRegistry meterRegistry) {
        return new MicrometerRetryMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerTimeoutMetrics micrometerTimeoutMetrics(MeterRegistry meterRegistry) {
        return new MicrometerTimeoutMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerCacheMetrics micrometerCacheMetrics(MeterRegistry meterRegistry) {
        return new MicrometerCacheMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerCaffeineCacheMetricCollector micrometerCaffeineCacheMetricsCollector(MeterRegistry meterRegistry) {
        return new MicrometerCaffeineCacheMetricCollector(meterRegistry);
    }

    @DefaultComponent
    default MicrometerS3ClientMetricsFactory micrometerS3ClientMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerS3ClientMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerS3KoraClientMetricsFactory micrometerS3KoraClientMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerS3KoraClientMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerCamundaEngineBpmnMetricsFactory micrometerCamundaEngineBpmnMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerCamundaEngineBpmnMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerCamundaRestMetricsFactory micrometerCamundaRestMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerCamundaRestMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerZeebeWorkerMetricsFactory micrometerZeebeWorkerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerZeebeWorkerMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerZeebeClientWorkerJobMetricsFactory micrometerZeebeClientWorkerJobMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerZeebeClientWorkerJobMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerMeterProvider micrometerMeterProvider(MeterRegistry registry, @Nullable CallbackRegistrar callbackRegistrar) {
        return MicrometerMeterProvider.builder(registry)
            .setCallbackRegistrar(callbackRegistrar)
            .build();
    }

    @DefaultComponent
    default CommandLatencyRecorderFactory micrometerLettuceCommandLatencyRecorderFactory(MeterRegistry registry) {
        return new MicrometerLettuceCommandLatencyRecorderFactory(registry);
    }
}

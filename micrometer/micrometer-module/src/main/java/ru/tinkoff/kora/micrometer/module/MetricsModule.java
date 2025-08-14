package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentelemetry.contrib.metrics.micrometer.CallbackRegistrar;
import io.opentelemetry.contrib.metrics.micrometer.MicrometerMeterProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.CommandLatencyRecorderFactory;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.HttpClientConfig;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.micrometer.module.cache.MicrometerCacheMetrics;
import ru.tinkoff.kora.micrometer.module.cache.caffeine.MicrometerCaffeineCacheMetricCollector;
import ru.tinkoff.kora.micrometer.module.cache.redis.lettuce.MicrometerLettuceCommandLatencyRecorderFactory;
import ru.tinkoff.kora.micrometer.module.camunda.engine.bpmn.MicrometerCamundaEngineBpmnMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.rest.MicrometerCamundaRestMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.zeebe.job.MicrometerZeebeClientWorkerJobMetricsFactory;
import ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker.MicrometerZeebeWorkerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.db.MicrometerDataBaseMetricWriterFactory;
import ru.tinkoff.kora.micrometer.module.grpc.client.MicrometerGrpcClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.DefaultMicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.grpc.client.tag.MicrometerGrpcClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.grpc.server.MicrometerGrpcServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.grpc.server.tag.DefaultMicrometerGrpcServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.grpc.server.tag.MicrometerGrpcServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.client.MicrometerHttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.client.tag.MicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.client.tag.Opentelemetry120MicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.client.tag.Opentelemetry123MicrometerHttpClientTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerHttpServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerPrivateApiMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DefaultMicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.tag.Opentelemetry123MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.jms.consumer.MicrometerJmsConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.MicrometerKafkaConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.MicrometerKafkaConsumerTagsProvider;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.Opentelemetry120KafkaConsumerTagsProvider;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.Opentelemetry123KafkaConsumerTagsProvider;
import ru.tinkoff.kora.micrometer.module.kafka.producer.MicrometerKafkaProducerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.kafka.producer.tag.MicrometerKafkaProducerTagsProvider;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerCircuitBreakerMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerFallbackMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerRetryMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerTimeoutMetrics;
import ru.tinkoff.kora.micrometer.module.s3.client.MicrometerS3ClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.s3.client.MicrometerS3KoraClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.scheduling.MicrometerSchedulingMetricsFactory;
import ru.tinkoff.kora.micrometer.module.soap.client.MicrometerSoapClientMetricsFactory;
import ru.tinkoff.kora.micrometer.prometheus.kora.KoraMeterRegistry;

import java.util.Optional;

public interface MetricsModule {

    default MetricsConfig globalMetricsConfig(Config config, ConfigValueExtractor<MetricsConfig> extractor) {
        var configValue = config.get("metrics");
        return Optional.ofNullable(extractor.extract(configValue)).orElseThrow(() -> ConfigValueExtractionException.missingValueAfterParse(configValue));
    }

    @Root
    default Wrapped<PrometheusMeterRegistry> prometheusMeterRegistry(MetricsConfig globalMetricsConfig,
                                                                     All<PrometheusMeterRegistryInitializer> initializers) {
        return switch (globalMetricsConfig.opentelemetrySpec()) {
            case V120 -> new PrometheusMeterRegistryWrapper(initializers, () -> new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
            case V123 -> new PrometheusMeterRegistryWrapper(initializers, () -> new KoraMeterRegistry(PrometheusConfig.DEFAULT));
        };
    }

    @DefaultComponent
    default MicrometerHttpServerTagsProvider micrometerHttpServerTagsProvider(HttpServerConfig config, MetricsConfig globalMetricsConfig) {
        return switch (globalMetricsConfig.opentelemetrySpec()) {
            case V120 -> new DefaultMicrometerHttpServerTagsProvider();
            case V123 -> new Opentelemetry123MicrometerHttpServerTagsProvider();
        };
    }

    @DefaultComponent
    default MicrometerHttpServerMetricsFactory micrometerHttpServerMetricsFactory(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider, MetricsConfig metricsConfig) {
        return new MicrometerHttpServerMetricsFactory(meterRegistry, httpServerTagsProvider, metricsConfig);
    }

    @DefaultComponent
    default MicrometerHttpClientTagsProvider micrometerHttpClientTagsProvider(HttpClientConfig httpClientConfig, MetricsConfig globalMetricsConfig) {
        return switch (globalMetricsConfig.opentelemetrySpec()) {
            case V120 -> new Opentelemetry120MicrometerHttpClientTagsProvider();
            case V123 -> new Opentelemetry123MicrometerHttpClientTagsProvider();
        };
    }

    @DefaultComponent
    default MicrometerHttpClientMetricsFactory micrometerHttpClientMetricsFactory(MeterRegistry meterRegistry,
                                                                                  MetricsConfig metricsConfig,
                                                                                  MicrometerHttpClientTagsProvider tagsProvider) {
        return new MicrometerHttpClientMetricsFactory(meterRegistry, metricsConfig, tagsProvider);
    }

    @DefaultComponent
    default MicrometerSoapClientMetricsFactory micrometerSoapClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerSoapClientMetricsFactory(meterRegistry, metricsConfig);
    }

    @DefaultComponent
    default MicrometerPrivateApiMetrics micrometerPrivateApiMetrics(PrometheusMeterRegistry meterRegistry) {
        return new MicrometerPrivateApiMetrics(meterRegistry);
    }

    @DefaultComponent
    default MicrometerGrpcServerTagsProvider micrometerGrpcServerTagsProvider(MetricsConfig globalMetricsConfig) {
        return new DefaultMicrometerGrpcServerTagsProvider();
    }

    @DefaultComponent
    default MicrometerGrpcServerMetricsFactory micrometerGrpcServerMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig, MicrometerGrpcServerTagsProvider grpcServerTagsProvider) {
        return new MicrometerGrpcServerMetricsFactory(meterRegistry, metricsConfig, grpcServerTagsProvider);
    }

    @DefaultComponent
    default MicrometerGrpcClientTagsProvider micrometerGrpcClientTagsProvider() {
        return new DefaultMicrometerGrpcClientTagsProvider();
    }

    @DefaultComponent
    default MicrometerGrpcClientMetricsFactory micrometerGrpcClientMetricsFactory(MeterRegistry registry,
                                                                                  MetricsConfig metricsConfig,
                                                                                  MicrometerGrpcClientTagsProvider tagsProvider) {
        return new MicrometerGrpcClientMetricsFactory(registry, metricsConfig, tagsProvider);
    }

    @DefaultComponent
    default MicrometerDataBaseMetricWriterFactory micrometerDataBaseMetricWriterFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerDataBaseMetricWriterFactory(meterRegistry, metricsConfig);
    }

    @DefaultComponent
    default MicrometerKafkaConsumerTagsProvider micrometerKafkaConsumerTagsProvider(MetricsConfig globalMetricsConfig) {
        return switch (globalMetricsConfig.opentelemetrySpec()) {
            case V120 -> new Opentelemetry120KafkaConsumerTagsProvider();
            case V123 -> new Opentelemetry123KafkaConsumerTagsProvider();
        };
    }

    @DefaultComponent
    default MicrometerKafkaConsumerMetricsFactory micrometerKafkaConsumerMetricsFactory(MeterRegistry meterRegistry,
                                                                                        MetricsConfig metricsConfig,
                                                                                        MicrometerKafkaConsumerTagsProvider tagsProvider) {
        return new MicrometerKafkaConsumerMetricsFactory(meterRegistry, metricsConfig, tagsProvider);
    }

    @DefaultComponent
    default MicrometerKafkaProducerMetricsFactory micrometerKafkaProducerMetricsFactory(MeterRegistry meterRegistry,
                                                                                        MetricsConfig metricsConfig,
                                                                                        MicrometerKafkaProducerTagsProvider tagsProvider) {
        return new MicrometerKafkaProducerMetricsFactory(meterRegistry, metricsConfig, tagsProvider);
    }

    @DefaultComponent
    default MicrometerJmsConsumerMetricsFactory micrometerJmsConsumerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerJmsConsumerMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerSchedulingMetricsFactory micrometerSchedulingMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerSchedulingMetricsFactory(meterRegistry, metricsConfig);
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
    default MicrometerS3ClientMetricsFactory micrometerS3ClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerS3ClientMetricsFactory(meterRegistry, metricsConfig);
    }

    @DefaultComponent
    default MicrometerS3KoraClientMetricsFactory micrometerS3KoraClientMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerS3KoraClientMetricsFactory(meterRegistry, metricsConfig);
    }

    @DefaultComponent
    default MicrometerCamundaEngineBpmnMetricsFactory micrometerCamundaEngineBpmnMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerCamundaEngineBpmnMetricsFactory(meterRegistry, metricsConfig);
    }

    @DefaultComponent
    default MicrometerCamundaRestMetricsFactory micrometerCamundaRestMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerCamundaRestMetricsFactory(meterRegistry, metricsConfig);
    }

    @DefaultComponent
    default MicrometerZeebeWorkerMetricsFactory micrometerZeebeWorkerMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        return new MicrometerZeebeWorkerMetricsFactory(meterRegistry, metricsConfig);
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
    default CommandLatencyRecorderFactory micrometerLettuceCommandLatencyRecorderFactory(MeterRegistry registry, MetricsConfig metricsConfig) {
        return new MicrometerLettuceCommandLatencyRecorderFactory(registry, metricsConfig);
    }
}

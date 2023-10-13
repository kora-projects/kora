package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.micrometer.module.cache.MicrometerCacheMetrics;
import ru.tinkoff.kora.micrometer.module.cache.caffeine.MicrometerCaffeineCacheMetricCollector;
import ru.tinkoff.kora.micrometer.module.db.MicrometerDataBaseMetricWriterFactory;
import ru.tinkoff.kora.micrometer.module.grpc.server.MicrometerGrpcServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.client.MicrometerHttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerHttpServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerPrivateApiMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DefaultMicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.jms.consumer.MicrometerJmsConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.MicrometerKafkaConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.kafka.producer.MicrometerKafkaProducerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerCircuitBreakerMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerFallbackMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerRetryMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerTimeoutMetrics;
import ru.tinkoff.kora.micrometer.module.scheduling.MicrometerSchedulingMetricsFactory;
import ru.tinkoff.kora.micrometer.module.soap.client.MicrometerSoapClientMetricsFactory;

public interface MetricsModule {

    @Root
    default PrometheusMeterRegistryWrapper prometheusMeterRegistry(All<PrometheusMeterRegistryInitializer> initializers) {
        return new PrometheusMeterRegistryWrapper(initializers);
    }

    @DefaultComponent
    default MicrometerHttpServerTagsProvider micrometerHttpServerTagsProvider() {
        return new DefaultMicrometerHttpServerTagsProvider();
    }

    @DefaultComponent
    default MicrometerHttpServerMetricsFactory micrometerHttpServerMetricsFactory(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider) {
        return new MicrometerHttpServerMetricsFactory(meterRegistry, httpServerTagsProvider);
    }

    @DefaultComponent
    default MicrometerHttpClientMetricsFactory micrometerHttpClientMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerHttpClientMetricsFactory(meterRegistry);
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
    default MicrometerGrpcServerMetricsFactory micrometerGrpcServerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerGrpcServerMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerDataBaseMetricWriterFactory micrometerDataBaseMetricWriterFactory(MeterRegistry meterRegistry) {
        return new MicrometerDataBaseMetricWriterFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerKafkaConsumerMetricsFactory micrometerKafkaConsumerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerKafkaConsumerMetricsFactory(meterRegistry);
    }

    @DefaultComponent
    default MicrometerKafkaProducerMetricsFactory micrometerKafkaProducerMetricsFactory(MeterRegistry meterRegistry) {
        return new MicrometerKafkaProducerMetricsFactory(meterRegistry);
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
    default MicrometerCaffeineCacheMetricCollector caffeineCacheMetricsCollector(MeterRegistry meterRegistry) {
        return new MicrometerCaffeineCacheMetricCollector(meterRegistry);
    }
}

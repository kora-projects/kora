package ru.tinkoff.kora.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.micrometer.module.cache.MicrometerCacheMetrics;
import ru.tinkoff.kora.micrometer.module.db.MicrometerDataBaseMetricWriterFactory;
import ru.tinkoff.kora.micrometer.module.grpc.server.MicrometerGrpcServerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.client.MicrometerHttpClientMetricsFactory;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerHttpServerMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.MicrometerPrivateApiMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DefaultMicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.micrometer.module.jms.consumer.MicrometerJmsConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.MicrometerKafkaConsumerMetrics;
import ru.tinkoff.kora.micrometer.module.resilient.MicrometerCircuitBreakerMetrics;
import ru.tinkoff.kora.micrometer.module.scheduling.MicrometerSchedulingMetricsFactory;
import ru.tinkoff.kora.micrometer.module.soap.client.MicrometerSoapClientMetricsFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public interface MetricsModule {
    default Initializer initializer(All<ValueOf<PrometheusMeterRegistryInitializer>> initializers) {
        return new Initializer(initializers.stream().map(ValueOf::get).toList());
    }

    default PrometheusMeterRegistry prometheusMeterRegistry(Initializer initializer) {
        return Initializer.meterRegistry.get();
    }

    class Initializer implements Lifecycle {
        private final static AtomicReference<PrometheusMeterRegistry> meterRegistry = new AtomicReference<>();

        public Initializer(List<PrometheusMeterRegistryInitializer> initializers) {
            if (Initializer.meterRegistry.get() != null) {
                return;
            }

            var meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            for (PrometheusMeterRegistryInitializer initializer : initializers) {
                meterRegistry = initializer.apply(meterRegistry);
            }

            Initializer.meterRegistry.set(meterRegistry);

            Metrics.globalRegistry.add(meterRegistry);
            new ClassLoaderMetrics().bindTo(meterRegistry);
            new JvmMemoryMetrics().bindTo(meterRegistry);
            new JvmGcMetrics().bindTo(meterRegistry);
            new ProcessorMetrics().bindTo(meterRegistry);
            new JvmThreadMetrics().bindTo(meterRegistry);
            new FileDescriptorMetrics().bindTo(meterRegistry);
            new UptimeMetrics().bindTo(meterRegistry);
        }

        @Override
        public Mono<Void> init() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> release() {
            return Mono.empty();
        }
    }

    @DefaultComponent
    default MicrometerHttpServerTagsProvider micrometerHttpServerTagsProvider() {
        return new DefaultMicrometerHttpServerTagsProvider();
    }

    @DefaultComponent
    default MicrometerHttpServerMetrics micrometerHttpServerMetricWriter(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider) {
        return new MicrometerHttpServerMetrics(meterRegistry, httpServerTagsProvider);
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
    default MicrometerKafkaConsumerMetrics micrometerKafkaConsumerMetrics(MeterRegistry meterRegistry) {
        return new MicrometerKafkaConsumerMetrics(meterRegistry);
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
    default MicrometerCacheMetrics micrometerCacheMetrics(MeterRegistry meterRegistry) {
        return new MicrometerCacheMetrics(meterRegistry);
    }
}

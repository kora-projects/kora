package io.koraframework.kafka.common.producer.telemetry.impl;

import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public class DefaultKafkaPublisherTelemetryFactory implements KafkaPublisherTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("kafka-publisher");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultKafkaPublisherLoggerFactory loggerFactory;
    @Nullable
    private final DefaultKafkaPublisherMetricsFactory metricsFactory;

    public DefaultKafkaPublisherTelemetryFactory(@Nullable Tracer tracer,
                                                 @Nullable MeterRegistry meterRegistry,
                                                 @Nullable DefaultKafkaPublisherLoggerFactory loggerFactory,
                                                 @Nullable DefaultKafkaPublisherMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public KafkaPublisherTelemetry get(String publisherConfig, String publisherCanonicalName, KafkaPublisherTelemetryConfig config, Properties properties) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopKafkaPublisherTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        final DefaultKafkaPublisherMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultKafkaPublisherMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopKafkaPublisherMetricsFactory.INSTANCE;
        }

        final DefaultKafkaPublisherLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultKafkaPublisherLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopKafkaPublisherLoggerFactory.INSTANCE;
        }

        return build(publisherConfig, publisherCanonicalName, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory, properties);
    }

    protected KafkaPublisherTelemetry build(String publisherConfig,
                                            String publisherCanonicalName,
                                            KafkaPublisherTelemetryConfig config,
                                            Tracer tracer,
                                            MeterRegistry meterRegistry,
                                            DefaultKafkaPublisherMetricsFactory metricsFactory,
                                            DefaultKafkaPublisherLoggerFactory loggerFactory,
                                            Properties properties) {
        return new DefaultKafkaPublisherTelemetry(publisherConfig, publisherCanonicalName, config, tracer, meterRegistry, metricsFactory, loggerFactory, properties);
    }
}

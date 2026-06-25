package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryConfig;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

import java.util.Properties;

public class DefaultKafkaConsumerTelemetryFactory implements KafkaConsumerTelemetryFactory {

    public static final Tracer NOOP_TRACER = TracerProvider.noop().get("kafka-listener");
    public static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultKafkaConsumerLoggerFactory loggerFactory;
    @Nullable
    private final DefaultKafkaConsumerMetricsFactory metricsFactory;

    public DefaultKafkaConsumerTelemetryFactory(@Nullable Tracer tracer,
                                                @Nullable MeterRegistry meterRegistry,
                                                @Nullable DefaultKafkaConsumerLoggerFactory loggerFactory,
                                                @Nullable DefaultKafkaConsumerMetricsFactory metricsFactory) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public KafkaConsumerTelemetry get(String listenerConfig, String listenerCanonicalName, Properties driverProperties, KafkaConsumerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopKafkaConsumerTelemetry.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        final DefaultKafkaConsumerMetricsFactory enabledMetricsFactory;
        if (metricEnabled) {
            enabledMetricsFactory = this.metricsFactory != null
                ? this.metricsFactory
                : DefaultKafkaConsumerMetricsFactory.INSTANCE;
        } else {
            enabledMetricsFactory = NoopKafkaConsumerMetricsFactory.INSTANCE;
        }

        final DefaultKafkaConsumerLoggerFactory enabledLoggerFactory;
        if (config.logging().enabled()) {
            enabledLoggerFactory = this.loggerFactory != null
                ? this.loggerFactory
                : DefaultKafkaConsumerLoggerFactory.INSTANCE;
        } else {
            enabledLoggerFactory = NoopKafkaConsumerLoggerFactory.INSTANCE;
        }
        return build(listenerConfig, listenerCanonicalName, config, tracer, meterRegistry, enabledMetricsFactory, enabledLoggerFactory, driverProperties);
    }

    protected KafkaConsumerTelemetry build(String listenerConfig,
                                           String listenerCanonicalName,
                                           KafkaConsumerTelemetryConfig config,
                                           Tracer tracer,
                                           MeterRegistry meterRegistry,
                                           DefaultKafkaConsumerMetricsFactory metricsFactory,
                                           DefaultKafkaConsumerLoggerFactory loggerFactory,
                                           Properties driverProperties) {
        return new DefaultKafkaConsumerTelemetry(listenerConfig, listenerCanonicalName, config, tracer, meterRegistry, metricsFactory, loggerFactory, driverProperties);
    }
}

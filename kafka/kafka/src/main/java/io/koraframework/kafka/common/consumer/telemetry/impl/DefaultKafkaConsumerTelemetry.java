package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.kafka.common.consumer.telemetry.*;
import io.koraframework.kafka.common.consumer.telemetry.impl.DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;

import java.util.Properties;

public class DefaultKafkaConsumerTelemetry implements KafkaConsumerTelemetry {

    public record TelemetryContext(KafkaConsumerTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Properties driverProperties,
                                   String listenerConfig,
                                   String listenerCanonicalName,
                                   String listenerSimpleName,
                                   String clientId,
                                   String groupId) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $KafkaConsumerTelemetryConfig_ConfigValueExtractor.KafkaConsumerTelemetryConfig_Impl(
                new $KafkaConsumerTelemetryConfig_KafkaConsumerLoggingConfig_ConfigValueExtractor.KafkaConsumerLoggingConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerMetricsConfig_ConfigValueExtractor.KafkaConsumerMetricsConfig_Defaults(),
                new $KafkaConsumerTelemetryConfig_KafkaConsumerTracingConfig_ConfigValueExtractor.KafkaConsumerTracingConfig_Defaults()
            ), false, false, DefaultKafkaConsumerTelemetryFactory.NOOP_METER_REGISTRY, DefaultKafkaConsumerTelemetryFactory.NOOP_TRACER, new Properties(), "none", "none", "none", "", "");
    }

    public static final String SYSTEM_CONFIG_PATH = "system.config";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultKafkaConsumerLoggerFactory.DefaultKafkaConsumerLogger logger;
    protected final DefaultKafkaConsumerMetrics metrics;

    public DefaultKafkaConsumerTelemetry(String listenerConfig,
                                         String listenerCanonicalName,
                                         KafkaConsumerTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultKafkaConsumerMetricsFactory metricsFactory,
                                         DefaultKafkaConsumerLoggerFactory loggerFactory,
                                         Properties driverProperties) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultKafkaConsumerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultKafkaConsumerTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config,
            isTracingEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            driverProperties,
            listenerConfig,
            listenerCanonicalName,
            listenerCanonicalName.substring(listenerCanonicalName.lastIndexOf('.') + 1),
            driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, ""),
            driverProperties.getProperty(ConsumerConfig.GROUP_ID_CONFIG, "")
        );

        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.context.meterRegistry();
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        logger.logPollStart();

        var span = context.isTracingEnabled()
            ? createSpanPoll().startSpan()
            : Span.getInvalid();

        return new DefaultKafkaConsumerPollObservation(context, logger, metrics, span);
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        this.metrics.reportTopicLag(partition, lag);
    }

    protected SpanBuilder createSpanPoll() {
        var span = context.tracer().spanBuilder("kafka.poll")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId())
            .setAttribute(SYSTEM_CONFIG_PATH, context.listenerConfig())
            .setAttribute(SYSTEM_NAME_SIMPLE, context.listenerSimpleName())
            .setAttribute(SYSTEM_NAME_CANONICAL, context.listenerCanonicalName())
            .setNoParent();
        for (var e : context.config().tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }
        return span;
    }
}

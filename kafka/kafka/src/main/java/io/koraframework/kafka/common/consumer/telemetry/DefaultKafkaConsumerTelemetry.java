package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.Properties;

public class DefaultKafkaConsumerTelemetry implements KafkaConsumerTelemetry {

    public record TelemetryContext(KafkaConsumerTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Logger logger,
                                   Properties driverProperties,
                                   String listenerName,
                                   String listenerImpl,
                                   String clientId,
                                   String groupId) {}

    public static final String SYSTEM_NAME = "system.name";
    public static final String SYSTEM_IMPL = "system.impl";

    protected final TelemetryContext context;
    protected final DefaultKafkaConsumerMetrics metrics;

    public DefaultKafkaConsumerTelemetry(String listenerName,
                                         String listenerImpl,
                                         KafkaConsumerTelemetryConfig config,
                                         Tracer tracer,
                                         MeterRegistry meterRegistry,
                                         DefaultKafkaConsumerMetricsFactory metricsFactory,
                                         Properties driverProperties) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultKafkaConsumerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultKafkaConsumerTelemetryFactory.NOOP_METER_REGISTRY;

        var logger = config.logging().enabled()
            ? LoggerFactory.getLogger(listenerImpl)
            : NOPLogger.NOP_LOGGER;

        this.context = new TelemetryContext(config,
            isTraceEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            logger,
            driverProperties,
            listenerName,
            listenerImpl,
            driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, ""),
            driverProperties.getProperty(ConsumerConfig.GROUP_ID_CONFIG, "")
        );

        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.context.meterRegistry;
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        context.logger.atTrace()
            .addKeyValue("listenerName", context.listenerName)
            .log("KafkaListener starting polling...");

        var span = context.isTraceEnabled
            ? createSpanPoll().startSpan()
            : Span.getInvalid();

        return new DefaultKafkaConsumerPollObservation(context, metrics, span);
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        this.metrics.reportTopicLag(partition, lag);
    }

    protected SpanBuilder createSpanPoll() {
        var span = context.tracer.spanBuilder("kafka.poll")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId)
            .setAttribute(SYSTEM_NAME, context.listenerName)
            .setAttribute(SYSTEM_IMPL, context.listenerImpl)
            .setNoParent();
        for (var e : context.config.tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }
        return span;
    }
}

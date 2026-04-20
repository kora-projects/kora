package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherMetricsFactory.DefaultKafkaPublisherMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.Properties;

public class DefaultKafkaPublisherTelemetry implements KafkaPublisherTelemetry {

    public record TelemetryContext(KafkaPublisherTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Logger logger,
                                   Properties driverProperties,
                                   String publisherName,
                                   String publisherImpl,
                                   String clientId) {}

    public static final String SYSTEM_NAME = "system.name";
    public static final String SYSTEM_IMPL = "system.impl";

    protected final TelemetryContext context;
    protected final DefaultKafkaPublisherMetrics metrics;

    public DefaultKafkaPublisherTelemetry(String publisherName,
                                          String publisherImpl,
                                          KafkaPublisherTelemetryConfig config,
                                          Tracer tracer,
                                          MeterRegistry meterRegistry,
                                          DefaultKafkaPublisherMetricsFactory metricsFactory,
                                          Properties driverProperties) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultKafkaPublisherTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultKafkaPublisherTelemetryFactory.NOOP_METER_REGISTRY;

        var logger = config.logging().enabled()
            ? LoggerFactory.getLogger(publisherImpl)
            : NOPLogger.NOP_LOGGER;

        this.context = new TelemetryContext(
            config,
            isTraceEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            logger,
            driverProperties,
            publisherName,
            publisherImpl,
            driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, "")
        );

        this.metrics = metricsFactory.create(context);
    }

    @Override
    public MeterRegistry meterRegistry() {
        return this.context.meterRegistry;
    }

    @Override
    public KafkaPublisherTransactionObservation observeTx() {
        var span = this.context.isTraceEnabled
            ? createTxSpan().startSpan()
            : Span.getInvalid();
        return new DefaultKafkaPublisherTransactionObservation(context, span);
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        var span = this.context.isTraceEnabled
            ? createSendSpan(topic).startSpan()
            : Span.getInvalid();
        return new DefaultKafkaPublisherRecordObservation(context, metrics, topic, span);
    }

    protected SpanBuilder createSendSpan(String topic) {
        var b = this.context.tracer.spanBuilder(topic + " send")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topic)
            .setAttribute(SYSTEM_NAME, context.publisherName)
            .setAttribute(SYSTEM_IMPL, context.publisherImpl);
        for (var entry : this.context.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b;
    }

    protected SpanBuilder createTxSpan() {
        var b = this.context.tracer.spanBuilder("producer transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(SYSTEM_NAME, context.publisherName)
            .setAttribute(SYSTEM_IMPL, context.publisherImpl);
        for (var entry : this.context.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b;
    }
}

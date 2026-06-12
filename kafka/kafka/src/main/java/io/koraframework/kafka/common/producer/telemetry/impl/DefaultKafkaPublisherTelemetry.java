package io.koraframework.kafka.common.producer.telemetry.impl;

import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherRecordObservation;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetryConfig;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTransactionObservation;
import io.koraframework.kafka.common.producer.telemetry.impl.DefaultKafkaPublisherMetricsFactory.DefaultKafkaPublisherMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

public class DefaultKafkaPublisherTelemetry implements KafkaPublisherTelemetry {

    public record TelemetryContext(KafkaPublisherTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   Properties driverProperties,
                                   String publisherConfig,
                                   String publisherCanonicalName,
                                   String publisherSimpleName,
                                   String clientId) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new KafkaPublisherTelemetryConfig() {
            @Override
            public KafkaProducerLoggingConfig logging() {
                return new KafkaProducerLoggingConfig() {};
            }

            @Override
            public KafkaProducerMetricsConfig metrics() {
                return new KafkaProducerMetricsConfig() {};
            }

            @Override
            public KafkaProducerTracingConfig tracing() {
                return new KafkaProducerTracingConfig() {};
            }
        }, false, false, DefaultKafkaPublisherTelemetryFactory.NOOP_METER_REGISTRY, DefaultKafkaPublisherTelemetryFactory.NOOP_TRACER, new Properties(), "none", "none", "none", "");
    }

    public static final String SYSTEM_CONFIG = "system.path";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultKafkaPublisherLoggerFactory.DefaultKafkaPublisherLogger logger;
    protected final DefaultKafkaPublisherMetrics metrics;

    public DefaultKafkaPublisherTelemetry(String publisherConfig,
                                          String publisherCanonicalName,
                                          KafkaPublisherTelemetryConfig config,
                                          Tracer tracer,
                                          MeterRegistry meterRegistry,
                                          DefaultKafkaPublisherMetricsFactory metricsFactory,
                                          DefaultKafkaPublisherLoggerFactory loggerFactory,
                                          Properties driverProperties) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultKafkaPublisherTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultKafkaPublisherTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(
            config,
            isTraceEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            driverProperties,
            publisherConfig,
            publisherCanonicalName,
            publisherCanonicalName.substring(publisherCanonicalName.lastIndexOf('.') + 1),
            driverProperties.getProperty(ConsumerConfig.CLIENT_ID_CONFIG, "")
        );

        this.metrics = metricsFactory.create(context);
        this.logger = loggerFactory.create(context);
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
        return new DefaultKafkaPublisherTransactionObservation(context, logger, span);
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        var span = this.context.isTraceEnabled
            ? createSendSpan(topic).startSpan()
            : Span.getInvalid();
        return new DefaultKafkaPublisherRecordObservation(context, logger, metrics, topic, span);
    }

    protected SpanBuilder createSendSpan(String topic) {
        var b = this.context.tracer.spanBuilder(topic + " send")
            .setSpanKind(SpanKind.PRODUCER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE, MessagingIncubatingAttributes.MessagingOperationTypeIncubatingValues.SEND)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, topic)
            .setAttribute(SYSTEM_CONFIG, context.publisherConfig)
            .setAttribute(SYSTEM_NAME_SIMPLE, context.publisherSimpleName)
            .setAttribute(SYSTEM_NAME_CANONICAL, context.publisherCanonicalName);
        for (var entry : this.context.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b;
    }

    protected SpanBuilder createTxSpan() {
        var b = this.context.tracer.spanBuilder("producer transaction")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(SYSTEM_CONFIG, context.publisherConfig)
            .setAttribute(SYSTEM_NAME_SIMPLE, context.publisherSimpleName)
            .setAttribute(SYSTEM_NAME_CANONICAL, context.publisherCanonicalName);
        for (var entry : this.context.config.tracing().attributes().entrySet()) {
            b.setAttribute(entry.getKey(), entry.getValue());
        }

        return b;
    }
}

package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.koraframework.jms.telemetry.JmsConsumerTelemetry;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryConfig;
import io.koraframework.jms.telemetry.$JmsConsumerTelemetryConfig_ConfigValueExtractor;
import io.koraframework.jms.telemetry.$JmsConsumerTelemetryConfig_JmsConsumerLoggingConfig_ConfigValueExtractor;
import io.koraframework.jms.telemetry.$JmsConsumerTelemetryConfig_JmsConsumerMetricsConfig_ConfigValueExtractor;
import io.koraframework.jms.telemetry.$JmsConsumerTelemetryConfig_JmsConsumerTracingConfig_ConfigValueExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;
import java.util.ArrayList;
import java.util.List;

public class DefaultJmsConsumerTelemetry implements JmsConsumerTelemetry {

    public record TelemetryContext(String queueName,
                                   JmsConsumerTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            "none",
            new $JmsConsumerTelemetryConfig_ConfigValueExtractor.JmsConsumerTelemetryConfig_Impl(
                new $JmsConsumerTelemetryConfig_JmsConsumerLoggingConfig_ConfigValueExtractor.JmsConsumerLoggingConfig_Defaults(),
                new $JmsConsumerTelemetryConfig_JmsConsumerMetricsConfig_ConfigValueExtractor.JmsConsumerMetricsConfig_Defaults(),
                new $JmsConsumerTelemetryConfig_JmsConsumerTracingConfig_ConfigValueExtractor.JmsConsumerTracingConfig_Defaults()
            ),
            false,
            false,
            DefaultJmsConsumerTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultJmsConsumerTelemetryFactory.NOOP_TRACER
        );
    }

    protected final TelemetryContext context;
    protected final DefaultJmsConsumerLoggerFactory.DefaultJmsConsumerLogger logger;
    protected final DefaultJmsConsumerMetricsFactory.DefaultJmsConsumerMetrics metrics;

    public DefaultJmsConsumerTelemetry(String queueName,
                                       JmsConsumerTelemetryConfig config,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry,
                                       DefaultJmsConsumerMetricsFactory metricsFactory,
                                       DefaultJmsConsumerLoggerFactory loggerFactory) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultJmsConsumerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultJmsConsumerTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(queueName, config, isTraceEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public JmsConsumerObservation observe(Message message) throws JMSException {
        var destination = readDestination(message);
        var span = this.context.isTraceEnabled()
            ? startSpan(message, destination).startSpan()
            : Span.getInvalid();
        return new DefaultJmsConsumerObservation(this.context, this.logger, this.metrics, message, destination, span);
    }

    protected SpanBuilder startSpan(Message message, String destination) throws JMSException {
        var parent = W3CTraceContextPropagator.getInstance()
            .extract(io.opentelemetry.context.Context.root(), message, MessageTextMapGetter.INSTANCE);

        var span = this.context.tracer()
            .spanBuilder(destination + " receive")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(parent)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.JMS)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, destination)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, message.getJMSMessageID());

        for (var attribute : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span;
    }

    protected String readDestination(Message message) throws JMSException {
        var destination = message.getJMSDestination();
        if (destination instanceof Queue queue) {
            return queue.getQueueName();
        }
        if (destination instanceof Topic topic) {
            return topic.getTopicName();
        }
        return this.context.queueName();
    }

    private enum MessageTextMapGetter implements TextMapGetter<Message> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Message carrier) {
            try {
                var enumeration = carrier.getPropertyNames();
                var headers = new ArrayList<String>();
                while (enumeration.hasMoreElements()) {
                    headers.add((String) enumeration.nextElement());
                }
                return headers;
            } catch (JMSException e) {
                return List.of();
            }
        }

        @Nullable
        @Override
        public String get(@Nullable Message carrier, String key) {
            if (carrier == null) {
                return null;
            }
            try {
                return carrier.getStringProperty(key);
            } catch (JMSException e) {
                return null;
            }
        }
    }
}

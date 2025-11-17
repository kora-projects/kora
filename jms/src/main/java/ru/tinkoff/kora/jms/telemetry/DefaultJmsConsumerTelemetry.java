package ru.tinkoff.kora.jms.telemetry;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultJmsConsumerTelemetry implements JmsConsumerTelemetry {
    private static final Timer NOOP_TIMER = new NoopTimer(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.TIMER));

    private final TelemetryConfig config;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<Tags, Timer> durationCache = new ConcurrentHashMap<>();
    private final Logger logger;

    public DefaultJmsConsumerTelemetry(TelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry, Logger logger) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.logger = logger;
    }

    @Override
    public JmsConsumerObservation observe(Message message) throws JMSException {
        var destination = message.getJMSDestination();
        var destinationString = "unknown";
        if (destination instanceof Queue queue) {
            destinationString = queue.getQueueName();
        } else if (destination instanceof Topic topic) {
            destinationString = topic.getTopicName();
        }
        var span = createSpan(message, destinationString);
        var duration = createDuration(message, destinationString);
        return new DefaultJmsConsumerObservation(message, span, duration, logger);
    }

    protected Meter.MeterProvider<Timer> createDuration(Message message, String destinationString) {
        if (!this.config.metrics().enabled()) {
            return _ -> NOOP_TIMER;
        }
        return tags -> {
            var effectiveTags = Tags.of(tags).and(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), destinationString));
            return durationCache.computeIfAbsent(effectiveTags, t -> {
                var b = Timer.builder("messaging.receive.duration")
                    .serviceLevelObjectives(config.metrics().slo())
                    .tag(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.JMS)
                    .tags(t);
                for (var e : this.config.metrics().tags().entrySet()) {
                    b.tag(e.getKey(), e.getValue());
                }
                return b.register(this.meterRegistry);
            });
        };
    }

    protected Span createSpan(Message message, String destinationString) throws JMSException {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var root = io.opentelemetry.context.Context.root();
        var parent = W3CTraceContextPropagator.getInstance().extract(root, message, MessageTextMapGetter.INSTANCE);

        var recordSpanBuilder = this.tracer
            .spanBuilder(destinationString + " receive")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.JMS)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, destinationString)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID, message.getJMSMessageID())
            .setParent(parent);
        for (var e : this.config.tracing().attributes().entrySet()) {
            recordSpanBuilder.setAttribute(e.getKey(), e.getValue());
        }
        return recordSpanBuilder.startSpan();
    }

    private enum MessageTextMapGetter implements TextMapGetter<Message> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Message carrier) {
            try {
                var enumeration = carrier.getPropertyNames();
                var headers = new ArrayList<String>();
                while (enumeration.hasMoreElements()) {
                    var nextElement = (String) enumeration.nextElement();
                    headers.add(nextElement);
                }
                return headers;
            } catch (JMSException e) {
                return List.of();
            }
        }

        @Nullable
        @Override
        public String get(@Nullable Message carrier, String key) {
            try {
                return carrier.getStringProperty(key);
            } catch (JMSException e) {
                return null;
            }
        }
    }
}

package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerReplyObservation;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerLoggerFactory.DefaultNatsConsumerLogger;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerMetricsFactory.DefaultNatsConsumerMetrics;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetry.TelemetryContext;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

public class DefaultNatsConsumerReplyObservation extends DefaultNatsConsumerOperationObservation implements NatsConsumerReplyObservation {
    private @Nullable Object value;

    public DefaultNatsConsumerReplyObservation(TelemetryContext context, DefaultNatsConsumerLogger logger, DefaultNatsConsumerMetrics metrics,
                                               Span span, String operation, String subject) {
        super(context, logger, metrics, span, operation, subject);
    }

    @Override
    public void observeData(@Nullable Object value) {
        this.value = value;
    }

    @Override
    public void observeRecord(Message message) {
        logger.record(message, value);
        observeResult(message);
        if (context.tracing()) {
            W3CTraceContextPropagator.getInstance().inject(
                Context.current().with(span), message.getHeaders(), (carrier, key, trace) -> carrier.put(key, trace));
        }
    }
}

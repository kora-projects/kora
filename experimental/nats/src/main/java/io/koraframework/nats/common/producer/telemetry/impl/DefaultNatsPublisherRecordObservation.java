package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherLoggerFactory.DefaultNatsPublisherLogger;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherMetricsFactory.DefaultNatsPublisherMetrics;
import io.koraframework.nats.common.producer.telemetry.impl.DefaultNatsPublisherTelemetry.TelemetryContext;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

public class DefaultNatsPublisherRecordObservation extends DefaultNatsPublisherOperationObservation implements NatsPublisherRecordObservation {
    private @Nullable Object value;

    public DefaultNatsPublisherRecordObservation(TelemetryContext context, DefaultNatsPublisherLogger logger, DefaultNatsPublisherMetrics metrics,
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

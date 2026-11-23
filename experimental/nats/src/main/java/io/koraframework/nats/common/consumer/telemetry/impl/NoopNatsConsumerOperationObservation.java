package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerOperationObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopNatsConsumerOperationObservation implements NatsConsumerOperationObservation {
    public static final NoopNatsConsumerOperationObservation INSTANCE = new NoopNatsConsumerOperationObservation();

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {
    }

    @Override
    public void observeError(Throwable error) {
    }
}

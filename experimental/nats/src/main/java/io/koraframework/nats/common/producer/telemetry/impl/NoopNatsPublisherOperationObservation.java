package io.koraframework.nats.common.producer.telemetry.impl;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopNatsPublisherOperationObservation implements NatsPublisherOperationObservation {
    public static final NoopNatsPublisherOperationObservation INSTANCE = new NoopNatsPublisherOperationObservation();

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

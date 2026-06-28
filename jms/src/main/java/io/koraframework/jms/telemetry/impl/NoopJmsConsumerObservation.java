package io.koraframework.jms.telemetry.impl;

import io.koraframework.jms.telemetry.JmsConsumerObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopJmsConsumerObservation implements JmsConsumerObservation {

    public static final NoopJmsConsumerObservation INSTANCE = new NoopJmsConsumerObservation();

    private NoopJmsConsumerObservation() {}

    @Override
    public void observeProcess() {}

    @Override
    public void observeError(Throwable e) {}

    @Override
    public void end() {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }
}

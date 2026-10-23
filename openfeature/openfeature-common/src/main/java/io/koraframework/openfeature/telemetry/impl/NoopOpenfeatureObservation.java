package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.FlagEvaluationDetails;
import io.koraframework.openfeature.telemetry.OpenfeatureObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopOpenfeatureObservation implements OpenfeatureObservation {

    public static final NoopOpenfeatureObservation INSTANCE = new NoopOpenfeatureObservation();

    private NoopOpenfeatureObservation() {}

    @Override
    public void observeResult(FlagEvaluationDetails<?> result) {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {}

    @Override
    public void observeError(Throwable e) {}
}

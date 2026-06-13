package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineObservation;
import io.opentelemetry.api.trace.Span;
import org.operaton.bpm.engine.delegate.DelegateExecution;

public final class NoopOperatonEngineObservation implements OperatonEngineObservation {

    public static final NoopOperatonEngineObservation INSTANCE = new NoopOperatonEngineObservation();

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }

    @Override
    public void observeExecution(DelegateExecution execution) {

    }
}

package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineObservation;
import io.opentelemetry.api.trace.Span;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public final class NoopCamundaEngineObservation implements CamundaEngineObservation {

    public static final NoopCamundaEngineObservation INSTANCE = new NoopCamundaEngineObservation();

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

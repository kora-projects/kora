package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.koraframework.camunda.zeebe.worker.telemetry.ZeebeWorkerObservation;
import io.opentelemetry.api.trace.Span;

public final class NoopZeebeWorkerObservation implements ZeebeWorkerObservation {

    public static final ZeebeWorkerObservation INSTANCE = new NoopZeebeWorkerObservation();

    private NoopZeebeWorkerObservation() {}

    @Override
    public void observeFinalCommandStep(FinalCommandStep<?> command) {

    }

    @Override
    public void observeHandle(String type, ActivatedJob job) {

    }

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
}

package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.opentelemetry.api.trace.Span;

public class NoopZeebeWorkerObservation implements ZeebeWorkerObservation {
    public static final NoopZeebeWorkerObservation INSTANCE = new NoopZeebeWorkerObservation();

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

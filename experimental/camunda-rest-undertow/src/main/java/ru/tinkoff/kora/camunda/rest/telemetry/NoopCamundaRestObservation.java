package ru.tinkoff.kora.camunda.rest.telemetry;

import io.opentelemetry.api.trace.Span;

import java.util.Map;

public class NoopCamundaRestObservation implements CamundaRestObservation {
    public static final NoopCamundaRestObservation INSTANCE = new NoopCamundaRestObservation();

    @Override
    public void observeRequest(String route, Map<String, String> pathParams) {

    }

    @Override
    public void observeResponseCode(int code) {

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

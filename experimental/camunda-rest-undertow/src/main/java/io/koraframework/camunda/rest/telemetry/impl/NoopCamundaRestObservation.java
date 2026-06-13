package io.koraframework.camunda.rest.telemetry.impl;

import io.koraframework.camunda.rest.telemetry.CamundaRestObservation;
import io.opentelemetry.api.trace.Span;

import java.util.Map;

public final class NoopCamundaRestObservation implements CamundaRestObservation {

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

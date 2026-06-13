package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestObservation;
import io.opentelemetry.api.trace.Span;

import java.util.Map;

public final class NoopOperatonRestObservation implements OperatonRestObservation {

    public static final NoopOperatonRestObservation INSTANCE = new NoopOperatonRestObservation();

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

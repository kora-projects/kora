package io.koraframework.camunda.rest.telemetry.impl;

import io.koraframework.camunda.rest.telemetry.CamundaRestObservation;
import io.koraframework.camunda.rest.telemetry.CamundaRestTelemetry;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public final class NoopCamundaRestTelemetry implements CamundaRestTelemetry {

    public static final NoopCamundaRestTelemetry INSTANCE = new NoopCamundaRestTelemetry();

    @Override
    public CamundaRestObservation observe(HttpServerExchange exchange, @Nullable String pathTemplate) {
        return NoopCamundaRestObservation.INSTANCE;
    }
}

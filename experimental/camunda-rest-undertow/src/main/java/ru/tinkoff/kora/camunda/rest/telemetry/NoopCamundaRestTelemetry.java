package ru.tinkoff.kora.camunda.rest.telemetry;

import io.undertow.server.HttpServerExchange;

public class NoopCamundaRestTelemetry implements CamundaRestTelemetry {
    public static final NoopCamundaRestTelemetry INSTANCE = new NoopCamundaRestTelemetry();

    @Override
    public CamundaRestObservation observe(HttpServerExchange exchange, String pathTemplate) {
        return NoopCamundaRestObservation.INSTANCE;
    }
}

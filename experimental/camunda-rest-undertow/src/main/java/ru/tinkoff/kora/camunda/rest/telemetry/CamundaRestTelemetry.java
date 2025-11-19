package ru.tinkoff.kora.camunda.rest.telemetry;

import io.undertow.server.HttpServerExchange;
import jakarta.annotation.Nullable;

public interface CamundaRestTelemetry {

    CamundaRestObservation observe(HttpServerExchange exchange, @Nullable String route);
}

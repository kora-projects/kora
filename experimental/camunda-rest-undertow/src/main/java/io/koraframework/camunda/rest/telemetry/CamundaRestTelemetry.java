package io.koraframework.camunda.rest.telemetry;

import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public interface CamundaRestTelemetry {

    CamundaRestObservation observe(HttpServerExchange exchange, @Nullable String route);
}

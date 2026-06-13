package io.koraframework.bpmn.operaton.rest.telemetry;

import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public interface OperatonRestTelemetry {

    OperatonRestObservation observe(HttpServerExchange exchange, @Nullable String route);
}

package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestObservation;
import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestTelemetry;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public final class NoopOperatonRestTelemetry implements OperatonRestTelemetry {

    public static final NoopOperatonRestTelemetry INSTANCE = new NoopOperatonRestTelemetry();

    @Override
    public OperatonRestObservation observe(HttpServerExchange exchange, @Nullable String pathTemplate) {
        return NoopOperatonRestObservation.INSTANCE;
    }
}

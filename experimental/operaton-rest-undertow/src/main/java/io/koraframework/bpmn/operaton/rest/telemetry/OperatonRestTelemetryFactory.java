package io.koraframework.bpmn.operaton.rest.telemetry;

import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;

public interface OperatonRestTelemetryFactory {

    OperatonRestTelemetry get(HttpServerTelemetryConfig telemetryConfig);
}

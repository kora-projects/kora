package io.koraframework.camunda.rest.telemetry;

import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;

public interface CamundaRestTelemetryFactory {

    CamundaRestTelemetry get(HttpServerTelemetryConfig telemetryConfig);
}

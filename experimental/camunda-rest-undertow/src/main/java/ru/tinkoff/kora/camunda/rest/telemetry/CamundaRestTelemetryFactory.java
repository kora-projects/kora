package ru.tinkoff.kora.camunda.rest.telemetry;

import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;

public interface CamundaRestTelemetryFactory {

    CamundaRestTelemetry get(HttpServerTelemetryConfig telemetryConfig);
}

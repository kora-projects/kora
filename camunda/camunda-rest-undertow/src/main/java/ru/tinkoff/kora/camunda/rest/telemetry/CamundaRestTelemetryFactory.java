package ru.tinkoff.kora.camunda.rest.telemetry;

import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;

public interface CamundaRestTelemetryFactory {

    CamundaRestTelemetry get(CamundaRestConfig.CamundaRestTelemetryConfig telemetryConfig);
}

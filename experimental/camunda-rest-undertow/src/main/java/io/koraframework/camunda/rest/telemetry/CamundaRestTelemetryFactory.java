package io.koraframework.camunda.rest.telemetry;

public interface CamundaRestTelemetryFactory {

    CamundaRestTelemetry get(CamundaRestTelemetryConfig telemetryConfig);
}

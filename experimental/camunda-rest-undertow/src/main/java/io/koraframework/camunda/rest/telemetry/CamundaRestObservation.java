package io.koraframework.camunda.rest.telemetry;

import io.koraframework.common.telemetry.Observation;

import java.util.Map;

public interface CamundaRestObservation extends Observation {
    void observeRequest(String route, Map<String, String> pathParams);

    void observeResponseCode(int code);

}

package ru.tinkoff.kora.camunda.rest.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

import java.util.Map;

public interface CamundaRestObservation extends Observation {
    void observeRequest(String route, Map<String, String> pathParams);

    void observeResponseCode(int code);

}

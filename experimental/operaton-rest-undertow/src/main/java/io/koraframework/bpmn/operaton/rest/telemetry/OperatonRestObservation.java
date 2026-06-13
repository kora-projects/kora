package io.koraframework.bpmn.operaton.rest.telemetry;

import io.koraframework.common.telemetry.Observation;

import java.util.Map;

public interface OperatonRestObservation extends Observation {

    void observeRequest(String route, Map<String, String> pathParams);

    void observeResponseCode(int code);

}

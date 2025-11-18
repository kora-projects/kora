package ru.tinkoff.kora.camunda.rest.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

public interface CamundaRestObservation extends Observation {
    void observeResponseCode(int code);

}

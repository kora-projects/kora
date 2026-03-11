package io.koraframework.database.common.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface DataBaseObservation extends Observation {

    void observeConnection();

    void observeStatement();
}

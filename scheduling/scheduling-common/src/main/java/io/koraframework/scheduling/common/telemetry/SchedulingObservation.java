package io.koraframework.scheduling.common.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface SchedulingObservation extends Observation {

    void observeRun();
}

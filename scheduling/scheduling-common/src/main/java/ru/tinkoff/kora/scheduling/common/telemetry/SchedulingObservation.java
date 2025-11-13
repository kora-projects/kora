package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

public interface SchedulingObservation extends Observation {

    void observeRun();
}

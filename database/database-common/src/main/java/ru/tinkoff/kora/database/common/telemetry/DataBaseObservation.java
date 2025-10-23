package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

public interface DataBaseObservation extends Observation {

    void observeConnection();

    void observeStatement();
}

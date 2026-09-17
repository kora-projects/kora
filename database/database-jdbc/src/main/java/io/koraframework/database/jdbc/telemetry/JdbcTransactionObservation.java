package io.koraframework.database.jdbc.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface JdbcTransactionObservation extends Observation {

    void observeCommit();

    void observeRollback();
}

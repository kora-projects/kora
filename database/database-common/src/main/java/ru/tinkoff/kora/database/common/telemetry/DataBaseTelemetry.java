package ru.tinkoff.kora.database.common.telemetry;

import ru.tinkoff.kora.database.common.QueryContext;

public interface DataBaseTelemetry {
    DataBaseObservation observe(QueryContext query);
}

package ru.tinkoff.kora.database.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.database.common.QueryContext;

public interface DataBaseTelemetry {
    MeterRegistry meterRegistry();

    DataBaseObservation observe(QueryContext query);
}

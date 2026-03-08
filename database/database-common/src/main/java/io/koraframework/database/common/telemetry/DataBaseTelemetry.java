package io.koraframework.database.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.koraframework.database.common.QueryContext;

public interface DataBaseTelemetry {
    MeterRegistry meterRegistry();

    DataBaseObservation observe(QueryContext query);
}

package io.koraframework.database.common.telemetry;

import io.koraframework.database.common.QueryContext;
import io.micrometer.core.instrument.MeterRegistry;

public interface DatabaseTelemetry {

    MeterRegistry meterRegistry();

    DatabaseObservation observe(QueryContext query);
}

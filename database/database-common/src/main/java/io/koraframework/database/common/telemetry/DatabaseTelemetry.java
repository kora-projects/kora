package io.koraframework.database.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.koraframework.database.common.QueryContext;

public interface DatabaseTelemetry {

    MeterRegistry meterRegistry();

    DatabaseObservation observe(QueryContext query);
}

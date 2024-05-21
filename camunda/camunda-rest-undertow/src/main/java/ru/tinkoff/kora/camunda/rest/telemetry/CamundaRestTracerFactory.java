package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CamundaRestTracerFactory {

    @Nullable
    CamundaRestTracer get(TelemetryConfig.TracingConfig tracing);
}

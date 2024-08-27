package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CamundaEngineTracerFactory {

    @Nullable
    CamundaEngineTracer get(TelemetryConfig.TracingConfig tracing);
}

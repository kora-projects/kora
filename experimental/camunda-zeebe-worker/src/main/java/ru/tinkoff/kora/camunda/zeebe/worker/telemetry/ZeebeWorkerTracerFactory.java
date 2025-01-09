package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface ZeebeWorkerTracerFactory {

    @Nullable
    ZeebeWorkerTracer get(TelemetryConfig.TracingConfig tracing);
}

package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CamundaEngineLoggerFactory {

    @Nullable
    CamundaEngineLogger get(CamundaEngineConfig.CamundaEngineLogConfig logging);
}

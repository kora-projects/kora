package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class DefaultCamundaEngineLoggerFactory implements CamundaEngineLoggerFactory {

    @Nullable
    @Override
    public CamundaEngineLogger get(CamundaEngineConfig.CamundaEngineLogConfig logging) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultCamundaEngineLogger(logging.stacktrace());
        } else {
            return null;
        }
    }
}

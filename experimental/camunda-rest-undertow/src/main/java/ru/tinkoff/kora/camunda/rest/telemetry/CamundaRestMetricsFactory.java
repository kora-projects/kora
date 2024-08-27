package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CamundaRestMetricsFactory {

    @Nullable
    CamundaRestMetrics get(TelemetryConfig.MetricsConfig config);
}

package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SoapClientMetricsFactory {

    @Nullable
    SoapClientMetrics get(TelemetryConfig.MetricsConfig config, String serviceClass, String serviceName, String soapMethod, String url);
}

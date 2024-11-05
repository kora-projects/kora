package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SoapClientMetricsFactory {

    /**
     * @see #get(TelemetryConfig.MetricsConfig, String, String, String, String)
     */
    @Deprecated
    @Nullable
    default SoapClientMetrics get(TelemetryConfig.MetricsConfig config, String serviceName, String soapMethod, String url) {
        return null;
    }

    @Nullable
    default SoapClientMetrics get(TelemetryConfig.MetricsConfig config, String serviceClass, String serviceName, String soapMethod, String url) {
        return get(config, serviceName, soapMethod, url);
    }
}

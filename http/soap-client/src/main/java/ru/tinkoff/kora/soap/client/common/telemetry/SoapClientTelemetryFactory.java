package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SoapClientTelemetryFactory {

    /**
     * @see #get(TelemetryConfig, String, String, String, String)
     */
    @Deprecated
    default SoapClientTelemetry get(TelemetryConfig config, String serviceName, String soapMethod, String url) {
        return null;
    }

    default SoapClientTelemetry get(TelemetryConfig config, String serviceClass, String serviceName, String soapMethod, String url) {
        return get(config, serviceName, soapMethod, url);
    }
}

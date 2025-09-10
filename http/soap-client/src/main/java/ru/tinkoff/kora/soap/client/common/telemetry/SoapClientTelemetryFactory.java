package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SoapClientTelemetryFactory {

    SoapClientTelemetry get(TelemetryConfig config, String serviceClass, String serviceName, String soapMethod, String url);
}

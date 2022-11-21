package ru.tinkoff.kora.soap.client.common.telemetry;

public interface SoapClientTelemetryFactory {
    SoapClientTelemetry get(String serviceName, String soapMethod, String url);
}

package ru.tinkoff.kora.soap.client.common.telemetry;

public interface SoapClientMetricsFactory {
    SoapClientMetrics get(String serviceName, String soapMethod, String url);
}

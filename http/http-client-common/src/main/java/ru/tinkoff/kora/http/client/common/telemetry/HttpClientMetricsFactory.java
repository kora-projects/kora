package ru.tinkoff.kora.http.client.common.telemetry;

public interface HttpClientMetricsFactory {
    HttpClientMetrics get(String clientName);
}

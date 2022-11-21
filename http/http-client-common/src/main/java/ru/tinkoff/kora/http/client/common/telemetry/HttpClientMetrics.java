package ru.tinkoff.kora.http.client.common.telemetry;

public interface HttpClientMetrics {
    void record(int statusCode, long processingTimeNanos, String method, String host, String scheme, String target);
}

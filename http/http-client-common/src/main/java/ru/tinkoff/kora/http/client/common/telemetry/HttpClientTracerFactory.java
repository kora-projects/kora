package ru.tinkoff.kora.http.client.common.telemetry;

public interface HttpClientTracerFactory {
    HttpClientTracer get(String clientName);
}

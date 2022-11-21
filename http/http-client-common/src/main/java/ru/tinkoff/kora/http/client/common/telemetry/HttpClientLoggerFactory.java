package ru.tinkoff.kora.http.client.common.telemetry;

public interface HttpClientLoggerFactory {
    HttpClientLogger get(String clientName);
}

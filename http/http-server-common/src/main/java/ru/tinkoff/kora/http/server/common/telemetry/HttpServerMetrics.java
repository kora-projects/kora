package ru.tinkoff.kora.http.server.common.telemetry;

public interface HttpServerMetrics {

    void requestStarted(String method, String route, String host, String scheme);

    void requestFinished(String method, String route, String host, String scheme, int statusCode, long processingTime);

}

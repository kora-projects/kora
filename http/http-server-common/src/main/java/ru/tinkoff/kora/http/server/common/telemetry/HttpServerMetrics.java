package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;

public interface HttpServerMetrics {

    void requestStarted(String method, String route, String host, String scheme);

    void requestFinished(String method, String route, String host, String scheme, int statusCode, long processingTimeNano);

    default void requestFinished(String method, String route, String host, String scheme, int statusCode, long processingTimeNano, @Nullable Throwable exception) {
        this.requestFinished(method, route, host, scheme, statusCode, processingTimeNano);
    }

}

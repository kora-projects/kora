package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpServerMetrics {

    void requestStarted(String method, String pathTemplate, String host, String scheme);

    @Deprecated
    default void requestFinished(String method, String pathTemplate, String host, String scheme, int statusCode, long processingTimeNanos) {

    }

    @Deprecated
    default void requestFinished(String method,
                                 String pathTemplate,
                                 String host,
                                 String scheme,
                                 int statusCode,
                                 long processingTimeNanos,
                                 @Nullable Throwable exception) {

    }

    default void requestFinished(int statusCode,
                                 HttpResultCode resultCode,
                                 String scheme,
                                 String host,
                                 String method,
                                 String pathTemplate,
                                 HttpHeaders headers,
                                 long processingTimeNanos,
                                 @Nullable Throwable exception) {
        if (exception == null) {
            requestFinished(method, pathTemplate, host, scheme, statusCode, processingTimeNanos);
        } else {
            requestFinished(method, pathTemplate, host, scheme, statusCode, processingTimeNanos, exception);
        }
    }
}

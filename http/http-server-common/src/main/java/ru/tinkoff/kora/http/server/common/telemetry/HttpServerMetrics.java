package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpServerMetrics {

    void requestStarted(String method, String pathTemplate, String host, String scheme);

    @Deprecated
    default void requestFinished(String method, String pathTemplate, String host, String scheme, int statusCode, long processingTimeNanos) {
        requestFinished(statusCode, HttpResultCode.SERVER_ERROR, scheme, host, method, pathTemplate, HttpHeaders.empty(), processingTimeNanos, null);
    }

    @Deprecated
    default void requestFinished(String method,
                                 String pathTemplate,
                                 String host,
                                 String scheme,
                                 int statusCode,
                                 long processingTimeNanos,
                                 @Nullable Throwable exception) {
        requestFinished(statusCode, HttpResultCode.SERVER_ERROR, scheme, host, method, pathTemplate, HttpHeaders.empty(), processingTimeNanos, exception);
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

    }
}

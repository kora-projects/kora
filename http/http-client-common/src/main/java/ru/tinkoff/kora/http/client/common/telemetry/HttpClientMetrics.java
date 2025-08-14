package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpClientMetrics {

    /**
     * @see #record(Integer, HttpResultCode, String, String, String, String, HttpHeaders, long, Throwable)
     */
    @Deprecated
    default void record(int statusCode, long processingTimeNanos, String method, String host, String scheme, String pathTemplate) {

    }

    default void record(@Nullable Integer statusCode,
                        HttpResultCode resultCode,
                        String scheme,
                        String host,
                        String method,
                        String pathTemplate,
                        HttpHeaders headers,
                        long processingTimeNanos,
                        @Nullable Throwable throwable) {
        int code = statusCode == null ? -1 : statusCode;
        record(code, processingTimeNanos, method, host, scheme, pathTemplate);
    }
}

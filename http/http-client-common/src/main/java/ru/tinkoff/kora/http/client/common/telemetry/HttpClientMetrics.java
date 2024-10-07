package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;

public interface HttpClientMetrics {

    @Deprecated
    default void record(int statusCode, long processingTimeNanos, String method, String host, String scheme, String pathTemplate) {

    }

    default void record(int statusCode,
                        HttpResultCode resultCode,
                        String scheme,
                        String host,
                        String method,
                        String pathTemplate,
                        long processingTimeNanos,
                        @Nullable Throwable throwable) {

    }
}

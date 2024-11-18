package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpClientTracer {

    interface HttpClientSpan {

        /**
         * @see #close(Integer, HttpResultCode, HttpHeaders, Throwable)
         */
        @Deprecated
        default void close(int code, @Nullable Throwable exception) {

        }

        default void close(@Nullable Integer statusCode,
                           HttpResultCode resultCode,
                           @Nullable HttpHeaders headers,
                           @Nullable Throwable exception) {
            close(statusCode == null ? -1 : statusCode, exception);
        }
    }

    HttpClientSpan createSpan(Context ctx, HttpClientRequest request);
}

package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpClientTracer {

    interface HttpClientSpan {

        void close(@Nullable Integer statusCode,
                   HttpResultCode resultCode,
                   @Nullable HttpHeaders headers,
                   @Nullable Throwable exception);
    }

    HttpClientSpan createSpan(Context ctx, HttpClientRequest request);
}

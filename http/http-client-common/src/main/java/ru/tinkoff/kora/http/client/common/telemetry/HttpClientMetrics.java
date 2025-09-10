package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpClientMetrics {

    void record(@Nullable Integer statusCode,
                HttpResultCode resultCode,
                String scheme,
                String host,
                String method,
                String pathTemplate,
                HttpHeaders headers,
                long processingTimeNanos,
                @Nullable Throwable throwable);
}

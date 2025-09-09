package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpClientLogger {
    boolean logRequest();

    boolean logRequestHeaders();

    boolean logRequestBody();

    boolean logResponse();

    boolean logResponseHeaders();

    boolean logResponseBody();

    void logRequest(String authority,
                    String method,
                    String path,
                    String pathTemplate,
                    String resolvedUri,
                    @Nullable String queryParams,
                    @Nullable HttpHeaders headers,
                    @Nullable String body);

    void logResponse(@Nullable Integer statusCode,
                     HttpResultCode resultCode,
                     String authority,
                     String method,
                     String path,
                     String pathTemplate,
                     long processingTime,
                     HttpHeaders headers,
                     @Nullable String body,
                     @Nullable Throwable exception);
}

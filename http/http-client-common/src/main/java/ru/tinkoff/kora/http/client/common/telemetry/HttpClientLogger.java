package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.net.URI;

public interface HttpClientLogger {
    boolean logRequest();

    boolean logRequestHeaders();

    boolean logRequestBody();

    boolean logResponse();

    boolean logResponseHeaders();

    boolean logResponseBody();

    void logRequest(String authority,
                    String method,
                    String operation,
                    URI resolvedUri,
                    @Nullable HttpHeaders headers,
                    @Nullable String body);

    void logResponse(String authority,
                     String method,
                     String operation,
                     URI resolvedUri,
                     long processingTime,
                     @Nullable Integer statusCode,
                     HttpResultCode resultCode,
                     @Nullable Throwable exception,
                     @Nullable HttpHeaders headers,
                     @Nullable String body);

}

package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;

import javax.annotation.Nullable;

public interface HttpClientLogger {
    boolean logRequest();

    boolean logRequestHeaders();

    boolean logRequestBody();

    boolean logResponse();

    boolean logResponseHeaders();

    boolean logResponseBody();

    void logRequest(String authority, String method, String operation, String resolvedUri, @Nullable HttpHeaders headers, @Nullable String body);

    void logResponse(String authority, String operation, long processingTime, @Nullable Integer statusCode, HttpResultCode resultCode, @Nullable Throwable exception, @Nullable HttpHeaders headers, @Nullable String body);

}

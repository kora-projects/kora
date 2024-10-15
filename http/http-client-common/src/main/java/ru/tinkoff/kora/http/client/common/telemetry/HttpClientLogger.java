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

    default void logRequest(String authority,
                            String method,
                            String path,
                            String pathTemplate,
                            String resolvedUri,
                            @Nullable String queryParams,
                            @Nullable HttpHeaders headers,
                            @Nullable String body) {
        logRequest(authority, method, method + ' ' + pathTemplate, resolvedUri, headers, body);
    }

    /**
     * @see #logRequest(String, String, String, String, String, String, HttpHeaders, String)
     */
    @Deprecated
    default void logRequest(String authority, String method, String operation, String resolvedUri, @Nullable HttpHeaders headers, @Nullable String body) {

    }

    default void logResponse(@Nullable Integer statusCode,
                             HttpResultCode resultCode,
                             String authority,
                             String method,
                             String path,
                             String pathTemplate,
                             long processingTime,
                             HttpHeaders headers,
                             @Nullable String body,
                             @Nullable Throwable exception) {
        logResponse(authority, method + ' ' + pathTemplate, processingTime, statusCode == -1 ? null : statusCode, resultCode, exception, headers, body);
    }

    /**
     * @see #logResponse(Integer, HttpResultCode, String, String, String, String, long, HttpHeaders, String, Throwable)
     */
    @Deprecated
    default void logResponse(String authority, String operation, long processingTime, @Nullable Integer statusCode, HttpResultCode resultCode, @Nullable Throwable exception, @Nullable HttpHeaders headers, @Nullable String body) {
    }
}

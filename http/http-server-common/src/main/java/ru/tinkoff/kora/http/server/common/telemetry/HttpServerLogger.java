package ru.tinkoff.kora.http.server.common.telemetry;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface HttpServerLogger {

    boolean isEnabled();

    default void logStart(String method,
                          String path,
                          String pathTemplate,
                          Map<String, ? extends Collection<String>> queryParams,
                          @Nullable HttpHeaders headers) {
        logStart(method + ' ' + pathTemplate, headers);
    }

    default void logEnd(int statusCode,
                        HttpResultCode resultCode,
                        String method,
                        String path,
                        String pathTemplate,
                        long processingTime,
                        Map<String, ? extends Collection<String>> queryParams,
                        @Nullable HttpHeaders headers,
                        @Nullable Throwable exception) {
        logEnd(method + ' ' + pathTemplate, statusCode, resultCode, processingTime, headers, exception);
    }

    /**
     * @see #logStart(String, String, String, Map, HttpHeaders)
     */
    @Deprecated
    default void logStart(String operation, HttpHeaders headers) {

    }

    /**
     * @see #logEnd(int, HttpResultCode, String, String, String, long, Map, HttpHeaders, Throwable)
     */
    @Deprecated
    default void logEnd(String operation,
                        Integer statusCode,
                        HttpResultCode resultCode,
                        long processingTime,
                        @Nullable HttpHeaders headers,
                        @Nullable Throwable exception) {

    }
}

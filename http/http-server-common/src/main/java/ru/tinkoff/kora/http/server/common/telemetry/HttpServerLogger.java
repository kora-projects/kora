package ru.tinkoff.kora.http.server.common.telemetry;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface HttpServerLogger {

    boolean isEnabled();

    default void logStart(String operation,
                          Map<String, ? extends Collection<String>> queryParams,
                          @Nullable HttpHeaders headers) {
        logStart(operation, headers);
    }

    default void logEnd(String operation,
                        Integer statusCode,
                        HttpResultCode resultCode,
                        long processingTime,
                        Map<String, ? extends Collection<String>> queryParams,
                        @Nullable HttpHeaders headers,
                        @Nullable Throwable exception) {
        logEnd(operation, statusCode, resultCode, processingTime, headers, exception);
    }

    /**
     * @see #logStart(String, Map, HttpHeaders)
     */
    @Deprecated
    default void logStart(String operation, @Nullable HttpHeaders headers) {
    }

    /**
     * @see #logEnd(String, Integer, HttpResultCode, long, Map, HttpHeaders, Throwable)
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

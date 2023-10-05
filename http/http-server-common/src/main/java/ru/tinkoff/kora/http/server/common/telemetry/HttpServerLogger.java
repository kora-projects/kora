package ru.tinkoff.kora.http.server.common.telemetry;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface HttpServerLogger {

    boolean isEnabled();

    void logStart(String operation, @Nullable HttpHeaders headers);

    void logEnd(String operation,
                Integer statusCode,
                HttpResultCode resultCode,
                long processingTime,
                @Nullable HttpHeaders headers,
                @Nullable Throwable exception);
}

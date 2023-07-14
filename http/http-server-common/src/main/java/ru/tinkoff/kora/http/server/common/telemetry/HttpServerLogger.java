package ru.tinkoff.kora.http.server.common.telemetry;


import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;

import javax.annotation.Nullable;

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

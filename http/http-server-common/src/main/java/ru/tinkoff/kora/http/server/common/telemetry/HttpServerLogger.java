package ru.tinkoff.kora.http.server.common.telemetry;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface HttpServerLogger {

    boolean isEnabled();

    void logStart(String method,
                  String path,
                  String pathTemplate,
                  Map<String, ? extends Collection<String>> queryParams,
                  @Nullable HttpHeaders headers);

    void logEnd(int statusCode,
                HttpResultCode resultCode,
                String method,
                String path,
                String pathTemplate,
                long processingTime,
                Map<String, ? extends Collection<String>> queryParams,
                @Nullable HttpHeaders headers,
                @Nullable Throwable exception);
}

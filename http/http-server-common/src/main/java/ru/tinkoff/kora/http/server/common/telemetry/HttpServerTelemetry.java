package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;

public interface HttpServerTelemetry {
    HttpServerTelemetryContext EMPTY_CTX = (statusCode, resultCode, headers, exception) -> {};
    HttpServerTelemetry EMPTY = (request, routeTemplate) -> EMPTY_CTX;

    interface HttpServerTelemetryContext {
        void close(int statusCode, HttpResultCode resultCode, HttpHeaders headers, @Nullable Throwable exception);
    }

    HttpServerTelemetryContext get(PublicApiRequest request, @Nullable String routeTemplate);
}

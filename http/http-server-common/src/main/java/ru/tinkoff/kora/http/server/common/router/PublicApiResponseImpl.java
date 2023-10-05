package ru.tinkoff.kora.http.server.common.router;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;

import java.util.concurrent.CompletableFuture;

final class PublicApiResponseImpl implements PublicApiResponse {
    private final HttpServerTelemetry.HttpServerTelemetryContext ctx;
    private final CompletableFuture<HttpServerResponse> response;

    PublicApiResponseImpl(HttpServerTelemetry.HttpServerTelemetryContext ctx, CompletableFuture<HttpServerResponse> response) {
        this.ctx = ctx;
        this.response = response;
    }

    @Override
    public CompletableFuture<HttpServerResponse> response() {
        return this.response;
    }

    @Override
    public void closeSendResponseSuccess(int code, HttpHeaders headers, @Nullable Throwable t) {
        var resultCode = HttpResultCode.fromStatusCode(code);
        ctx.close(code, resultCode, headers, t);
    }

    @Override
    public void closeBodyError(int responseCode, Throwable t) {
        ctx.close(responseCode, HttpResultCode.SERVER_ERROR, null, t);
    }

    @Override
    public void closeConnectionError(int responseCode, Throwable t) {
        ctx.close(responseCode, HttpResultCode.CONNECTION_ERROR, null, t);
    }
}

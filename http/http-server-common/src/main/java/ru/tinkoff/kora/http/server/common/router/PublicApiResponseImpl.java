package ru.tinkoff.kora.http.server.common.router;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;

final class PublicApiResponseImpl implements PublicApiResponse {
    private final HttpServerTelemetry.HttpServerTelemetryContext ctx;
    private final HttpServerResponse response;
    private final Throwable error;

    PublicApiResponseImpl(HttpServerTelemetry.HttpServerTelemetryContext ctx, HttpServerResponse response, Throwable error) {
        this.ctx = ctx;
        this.response = response;
        this.error = error;
    }

    @Override
    public HttpServerResponse response() {
        return this.response;
    }

    @Nullable
    @Override
    public Throwable error() {
        return this.error;
    }

    @Override
    public void closeSendResponseSuccess(int code, HttpHeaders headers, @Nullable Throwable t) {
        var resultCode = HttpResultCode.fromStatusCode(code);
        ctx.close(code, resultCode, headers, t);
    }

    @Override
    public void closeBodyError(int responseCode, Throwable t) {
        ctx.close(responseCode, HttpResultCode.SERVER_ERROR, HttpHeaders.empty(), t);
    }

    @Override
    public void closeConnectionError(int responseCode, Throwable t) {
        ctx.close(responseCode, HttpResultCode.CONNECTION_ERROR, HttpHeaders.empty(), t);
    }
}

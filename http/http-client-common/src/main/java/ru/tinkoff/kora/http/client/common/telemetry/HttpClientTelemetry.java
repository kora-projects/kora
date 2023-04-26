package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import javax.annotation.Nullable;

public interface HttpClientTelemetry {

    interface HttpServerTelemetryContext {
        HttpClientRequest request();

        HttpClientResponse close(@Nullable HttpClientResponse response, @Nullable Throwable exception);
    }

    boolean isEnabled();

    HttpServerTelemetryContext get(Context ctx, HttpClientRequest request);
}

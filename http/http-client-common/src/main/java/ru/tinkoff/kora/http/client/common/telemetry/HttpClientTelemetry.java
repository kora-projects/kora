package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

public interface HttpClientTelemetry {

    interface HttpClientTelemetryContext {
        HttpClientRequest request();

        HttpClientResponse close(@Nullable HttpClientResponse response, @Nullable Throwable exception);
    }

    boolean isEnabled();

    @Nullable
    HttpClientTelemetryContext get(Context ctx, HttpClientRequest request);
}

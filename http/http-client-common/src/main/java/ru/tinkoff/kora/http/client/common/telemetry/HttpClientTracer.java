package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

public interface HttpClientTracer {
    interface HttpClientSpan {
        void close(@Nullable Throwable exception);
    }

    HttpClientSpan createSpan(Context ctx, HttpClientRequest request);
}

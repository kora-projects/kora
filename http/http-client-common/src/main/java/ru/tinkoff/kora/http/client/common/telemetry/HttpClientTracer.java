package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;

import javax.annotation.Nullable;

public interface HttpClientTracer {
    interface HttpClientSpan {
        void close(@Nullable Throwable exception);
    }

    record CreateSpanResult(HttpClientSpan span, HttpClientRequest request) {}

    CreateSpanResult createSpan(Context ctx, HttpClientRequest request);
}

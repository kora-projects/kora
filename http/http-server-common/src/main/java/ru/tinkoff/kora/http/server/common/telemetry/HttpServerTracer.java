package ru.tinkoff.kora.http.server.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;

import javax.annotation.Nullable;

public interface HttpServerTracer {

    interface HttpServerSpan {
        void close(int statusCode, HttpResultCode resultCode, @Nullable Throwable exception);
    }

    interface HeadersSetter<H> {
        void set(H headers, String key, String value);
    }

    <T> void inject(Context context, T headers, HeadersSetter<T> headersSetter);

    HttpServerSpan createSpan(String template, PublicApiHandler.PublicApiRequest routerRequest);
}

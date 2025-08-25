package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpServerExchange;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;

public final class UndertowContext {

    private UndertowContext() { }

    private static final Context.Key<HttpServerExchange> KEY = new Context.Key<>() {
        @Override
        protected HttpServerExchange copy(HttpServerExchange object) {
            return null;
        }
    };

    public static void set(Context ctx, HttpServerExchange exchange) {
        ctx.set(KEY, exchange);
    }

    public static void clear(Context ctx) {
        ctx.remove(KEY);
    }

    @Nullable
    public static HttpServerExchange get(Context ctx) {
        return ctx.get(KEY);
    }


}

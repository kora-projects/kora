package ru.tinkoff.kora.http.server.undertow;

import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public final class UndertowContext {

    public final HttpServerExchange exchange;

    public UndertowContext(HttpServerExchange exchange) {
        this.exchange = exchange;
    }

    public static final ScopedValue<UndertowContext> VALUE = ScopedValue.newInstance();

    @Nullable
    public static UndertowContext get() {
        if (VALUE.isBound()) {
            return VALUE.get();
        } else {
            return null;
        }
    }

    @Nullable
    public static HttpServerExchange getExchange() {
        if (VALUE.isBound()) {
            return VALUE.get().exchange;
        } else {
            return null;
        }
    }
}

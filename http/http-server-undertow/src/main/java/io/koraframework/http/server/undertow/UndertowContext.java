package io.koraframework.http.server.undertow;

import io.koraframework.common.context.*;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public record UndertowContext(HttpServerExchange exchange) {

    public static final KoraContext.Key<UndertowContext> UNDERTOW_KEY = KoraContext.Key.create();

    @Nullable
    public static UndertowContext get() {
        KoraContext current = KoraContext.current();
        if (current != null) {
            return current.get(UNDERTOW_KEY);
        }
        return null;
    }

    @Nullable
    public static HttpServerExchange getExchange() {
        KoraContext current = KoraContext.current();
        if (current != null) {
            UndertowContext undertowCtx = current.get(UNDERTOW_KEY);
            if (undertowCtx != null) {
                return undertowCtx.exchange;
            }
        }
        return null;
    }
}

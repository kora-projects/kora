package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

public interface HttpServerRequestHandler {

    String method();

    String routeTemplate();

    HttpServerResponse handle(Context ctx, HttpServerRequest request) throws Exception;

    default boolean enabled() {
        return true;
    }

    interface HandlerFunction {
        HttpServerResponse apply(Context context, HttpServerRequest request) throws Exception;
    }
}

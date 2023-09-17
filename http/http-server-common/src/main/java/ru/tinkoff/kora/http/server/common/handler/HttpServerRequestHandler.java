package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.util.concurrent.CompletionStage;

public interface HttpServerRequestHandler {
    String method();

    String routeTemplate();

    CompletionStage<HttpServerResponse> handle(Context ctx, HttpServerRequest request) throws Exception;

    interface HandlerFunction {
        CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request) throws Exception;
    }
}

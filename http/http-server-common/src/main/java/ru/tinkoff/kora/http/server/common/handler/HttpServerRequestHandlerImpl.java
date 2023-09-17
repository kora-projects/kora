package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.util.concurrent.CompletionStage;

import static ru.tinkoff.kora.http.common.HttpMethod.*;

public final class HttpServerRequestHandlerImpl implements HttpServerRequestHandler {
    private final String method;
    private final String routeTemplate;
    private final HandlerFunction handler;

    public HttpServerRequestHandlerImpl(String method, String routeTemplate, HandlerFunction handler) {
        this.method = method;
        this.routeTemplate = routeTemplate;
        this.handler = handler;
    }

    public static HttpServerRequestHandlerImpl of(String method, String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(method, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl get(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(GET, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl head(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(HEAD, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl post(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(POST, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl put(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(PUT, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl delete(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(DELETE, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl connect(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(CONNECT, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl options(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(OPTIONS, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl trace(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(TRACE, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl patch(String routeTemplate, HandlerFunction handler) {
        return new HttpServerRequestHandlerImpl(PATCH, routeTemplate, handler);
    }

    @Override
    public String method() {
        return this.method;
    }

    @Override
    public String routeTemplate() {
        return this.routeTemplate;
    }

    @Override
    public CompletionStage<HttpServerResponse> handle(Context ctx, HttpServerRequest request) throws Exception {
        return this.handler.apply(ctx, request);
    }
}

package ru.tinkoff.kora.http.server.common.handler;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.util.function.Function;

import static ru.tinkoff.kora.http.common.HttpMethod.*;

public class HttpServerRequestHandlerImpl implements HttpServerRequestHandler {
    private final String method;
    private final String routeTemplate;
    private final Function<HttpServerRequest, Mono<HttpServerResponse>> handler;

    public HttpServerRequestHandlerImpl(String method, String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        this.method = method;
        this.routeTemplate = routeTemplate;
        this.handler = handler;
    }

    public static HttpServerRequestHandlerImpl get(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(GET, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl head(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(HEAD, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl post(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(POST, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl put(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(PUT, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl delete(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(DELETE, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl connect(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(CONNECT, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl options(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(OPTIONS, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl trace(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
        return new HttpServerRequestHandlerImpl(TRACE, routeTemplate, handler);
    }

    public static HttpServerRequestHandlerImpl patch(String routeTemplate, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
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
    public Mono<HttpServerResponse> handle(HttpServerRequest request) {
        return this.handler.apply(request);
    }
}

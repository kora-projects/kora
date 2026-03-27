package io.koraframework.http.server.common.request;

import io.koraframework.http.server.common.response.HttpServerResponse;

public interface HttpServerRequestHandler {

    String method();

    String routeTemplate();

    HttpServerResponse handle(HttpServerRequest request) throws Exception;

    default boolean enabled() {
        return true;
    }

    interface HandlerFunction {
        HttpServerResponse apply(HttpServerRequest request) throws Exception;
    }
}

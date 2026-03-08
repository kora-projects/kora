package io.koraframework.http.server.common.handler;

import io.koraframework.http.server.common.HttpServerRequest;
import io.koraframework.http.server.common.HttpServerResponse;

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

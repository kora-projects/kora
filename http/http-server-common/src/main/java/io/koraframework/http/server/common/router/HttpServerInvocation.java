package io.koraframework.http.server.common.router;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;

public interface HttpServerInvocation {

    HttpServerRequest routedRequest();

    HttpServerResponse proceed(HttpServerRequest request) throws Exception;
}

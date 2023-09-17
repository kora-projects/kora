package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.CompletionStage;

public interface HttpServerInterceptor {
    CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain) throws Exception;

    interface InterceptChain {
        CompletionStage<HttpServerResponse> process(Context ctx, HttpServerRequest request) throws Exception;
    }

    static HttpServerInterceptor noop() {
        return (context, request, chain) -> chain.process(context, request);
    }
}

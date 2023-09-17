package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.CompletionStage;

public interface HttpClientInterceptor {
    CompletionStage<HttpClientResponse> processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) throws Exception;

    interface InterceptChain {
        CompletionStage<HttpClientResponse> process(Context ctx, HttpClientRequest request) throws Exception;
    }


    static HttpClientInterceptor noop() {
        return (ctx, chain, request) -> chain.process(ctx, request);
    }
}

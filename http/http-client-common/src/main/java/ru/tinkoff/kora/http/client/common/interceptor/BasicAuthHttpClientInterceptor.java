package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.auth.HttpClientTokenProvider;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BasicAuthHttpClientInterceptor implements HttpClientInterceptor {
    private final HttpClientTokenProvider tokenProvider;

    public BasicAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public CompletionStage<HttpClientResponse> processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) {
        return this.tokenProvider.getToken(request).thenCompose(token -> {
            try {
                if (token == null) {
                    return chain.process(ctx, request);
                }
                var modifiedRequest = request.toBuilder().header("authorization", "Basic " + token).build();
                return chain.process(ctx, modifiedRequest);
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        });
    }
}

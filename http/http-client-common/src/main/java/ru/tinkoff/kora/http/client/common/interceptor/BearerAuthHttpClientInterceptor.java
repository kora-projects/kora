package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.auth.HttpClientTokenProvider;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BearerAuthHttpClientInterceptor implements HttpClientInterceptor {

    private final HttpClientTokenProvider tokenProvider;

    public BearerAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public BearerAuthHttpClientInterceptor(String token) {
        var result = CompletableFuture.completedFuture(token);
        this.tokenProvider = request -> result;
    }

    @Override
    public CompletionStage<HttpClientResponse> processRequest(Context context, InterceptChain chain, HttpClientRequest request) {
        return this.tokenProvider.getToken(request).thenCompose(token -> {
            try {
                if (token == null) {
                    return chain.process(context, request);
                } else {
                    var modifiedRequest = request.toBuilder().header("authorization", "Bearer " + token).build();
                    return chain.process(context, modifiedRequest);
                }
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        });
    }
}

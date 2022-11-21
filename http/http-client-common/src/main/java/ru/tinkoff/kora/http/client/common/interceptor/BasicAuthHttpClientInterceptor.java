package ru.tinkoff.kora.http.client.common.interceptor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.auth.HttpClientTokenProvider;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.function.Function;

public class BasicAuthHttpClientInterceptor implements HttpClientInterceptor {
    private final HttpClientTokenProvider tokenProvider;

    public BasicAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
        return this.tokenProvider.getToken(request).flatMap(token -> {
            var modifiedRequest = request.toBuilder().header("authorization", "Basic " + token).build();
            return chain.apply(modifiedRequest);
        });
    }
}

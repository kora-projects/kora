package ru.tinkoff.kora.http.client.common.interceptor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.auth.HttpClientTokenProvider;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.Optional;
import java.util.function.Function;

public class BearerAuthHttpClientInterceptor implements HttpClientInterceptor {
    private final HttpClientTokenProvider tokenProvider;

    public BearerAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
        return this.tokenProvider.getToken(request).map(Optional::of).switchIfEmpty(Mono.just(Optional.empty()))
            .flatMap(token -> {
                if (token.isEmpty()) {
                    return chain.apply(request);
                } else {
                    var modifiedRequest = request.toBuilder().header("authorization", "Bearer " + token.get()).build();
                    return chain.apply(modifiedRequest);
                }
            });
    }
}

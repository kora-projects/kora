package ru.tinkoff.kora.http.client.common.interceptor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.function.Function;

public class RootUriInterceptor implements HttpClientInterceptor {
    private final String root;

    public RootUriInterceptor(String root) {
        this.root = root.endsWith("/")
            ? root.substring(0, root.length() - 1)
            : root;
    }

    @Override
    public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
        var template = request.uriTemplate().startsWith("/")
            ? request.uriTemplate()
            : "/" + request.uriTemplate();

        var r = request.toBuilder()
            .uriTemplate(this.root + template)
            .build();

        return chain.apply(r);
    }
}

package ru.tinkoff.kora.http.client.common.interceptor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.Objects;
import java.util.function.Function;

public final class ApiKeyHttpClientInterceptor implements HttpClientInterceptor {
    private final String parameterName;
    private final String secret;
    private final ApiKeyLocation parameterLocation;

    public enum ApiKeyLocation {
        HEADER, QUERY, COOKIE
    }

    public ApiKeyHttpClientInterceptor(ApiKeyLocation parameterLocation, String parameterName, String secret) {
        this.parameterName = parameterName;
        this.secret = Objects.requireNonNull(secret);
        this.parameterLocation = parameterLocation;
    }

    @Override
    public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
        var modifiedRequest = switch (this.parameterLocation) {
            case HEADER -> request.toBuilder().header(this.parameterName, this.secret);
            case QUERY -> request.toBuilder().queryParam(this.parameterName, this.secret);
            case COOKIE -> throw new IllegalStateException("TODO: cookies");
        };

        return chain.apply(modifiedRequest.build());
    }
}

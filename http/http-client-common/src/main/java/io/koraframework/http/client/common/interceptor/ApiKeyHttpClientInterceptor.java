package io.koraframework.http.client.common.interceptor;

import io.koraframework.http.client.common.auth.HttpClientTokenProvider;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.cookie.Cookie;

public final class ApiKeyHttpClientInterceptor implements HttpClientInterceptor {

    private final String parameterName;
    private final HttpClientTokenProvider tokenProvider;
    private final ApiKeyLocation parameterLocation;

    public enum ApiKeyLocation {
        HEADER, QUERY, COOKIE
    }

    public ApiKeyHttpClientInterceptor(ApiKeyLocation parameterLocation, String parameterName, HttpClientTokenProvider tokenProvider) {
        this.parameterName = parameterName;
        this.tokenProvider = tokenProvider;
        this.parameterLocation = parameterLocation;
    }

    public ApiKeyHttpClientInterceptor(ApiKeyLocation parameterLocation, String parameterName, String secret) {
        this(parameterLocation, parameterName, _ -> secret);
    }

    @Override
    public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
        var secret = this.tokenProvider.getToken(request);
        if (secret == null || secret.isBlank()) {
            return chain.process(request);
        }
        var builder = request.toBuilder();
        switch (this.parameterLocation) {
            case HEADER -> builder.header(this.parameterName, secret);
            case QUERY -> builder.queryParam(this.parameterName, secret);
            case COOKIE -> {
                var cookie = Cookie.of(this.parameterName, secret).toValue();
                var existing = request.headers().getFirst("Cookie");
                builder.header("Cookie", existing == null || existing.isBlank() ? cookie : existing + "; " + cookie);
            }
        }
        return chain.process(builder.build());
    }
}

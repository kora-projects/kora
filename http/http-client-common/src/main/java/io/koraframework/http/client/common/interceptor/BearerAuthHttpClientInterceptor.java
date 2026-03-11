package io.koraframework.http.client.common.interceptor;

import io.koraframework.http.client.common.auth.HttpClientTokenProvider;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;

public class BearerAuthHttpClientInterceptor implements HttpClientInterceptor {

    private final HttpClientTokenProvider tokenProvider;

    public BearerAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public BearerAuthHttpClientInterceptor(String token) {
        this.tokenProvider = _ -> token;
    }

    @Override
    public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
        var token = this.tokenProvider.getToken(request);
        if (token == null) {
            return chain.process(request);
        } else {
            var modifiedRequest = request.toBuilder().header("authorization", "Bearer " + token).build();
            return chain.process(modifiedRequest);
        }
    }
}

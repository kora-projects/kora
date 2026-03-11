package io.koraframework.http.client.common.interceptor;

import io.koraframework.http.client.common.auth.BasicAuthHttpClientTokenProvider;
import io.koraframework.http.client.common.auth.HttpClientTokenProvider;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;

public class BasicAuthHttpClientInterceptor implements HttpClientInterceptor {

    private final HttpClientTokenProvider tokenProvider;

    public BasicAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public BasicAuthHttpClientInterceptor(String username, String password) {
        this.tokenProvider = new BasicAuthHttpClientTokenProvider(username, password);
    }

    @Override
    public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
        var token = this.tokenProvider.getToken(request);
        if (token == null) {
            return chain.process(request);
        } else {
            var modifiedRequest = request.toBuilder().header("authorization", "Basic " + token).build();
            return chain.process(modifiedRequest);
        }
    }
}

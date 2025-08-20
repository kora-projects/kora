package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.auth.HttpClientTokenProvider;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

public class BearerAuthHttpClientInterceptor implements HttpClientInterceptor {

    private final HttpClientTokenProvider tokenProvider;

    public BearerAuthHttpClientInterceptor(HttpClientTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    public BearerAuthHttpClientInterceptor(String token) {
        this.tokenProvider = _ -> token;
    }

    @Override
    public HttpClientResponse processRequest(Context context, InterceptChain chain, HttpClientRequest request) throws Exception {
        var token = this.tokenProvider.getToken(request);
        if (token == null) {
            return chain.process(context, request);
        } else {
            var modifiedRequest = request.toBuilder().header("authorization", "Bearer " + token).build();
            return chain.process(context, modifiedRequest);
        }
    }
}

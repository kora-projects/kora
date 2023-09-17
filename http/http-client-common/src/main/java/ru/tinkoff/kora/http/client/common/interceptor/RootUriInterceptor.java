package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.CompletionStage;

public class RootUriInterceptor implements HttpClientInterceptor {
    private final String root;

    public RootUriInterceptor(String root) {
        this.root = root.endsWith("/")
            ? root.substring(0, root.length() - 1)
            : root;
    }

    @Override
    public CompletionStage<HttpClientResponse> processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) throws Exception {
        var template = request.uriTemplate().startsWith("/")
            ? request.uriTemplate()
            : "/" + request.uriTemplate();

        var r = request.toBuilder()
            .uriTemplate(this.root + template)
            .build();

        return chain.process(ctx, r);
    }
}

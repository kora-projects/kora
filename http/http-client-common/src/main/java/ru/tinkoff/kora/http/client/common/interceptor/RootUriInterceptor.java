package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.DefaultHttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.net.URI;
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
        if (request.uri().getScheme() != null) {
            return chain.process(ctx, request);
        }

        var uri = request.uri().toString();
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        var prefixed = this.root + uri;
        var parsed = URI.create(prefixed);

        var r = new DefaultHttpClientRequest(
            request.method(),
            parsed,
            request.uriTemplate(),
            request.headers(),
            request.body(),
            request.requestTimeout()
        );

        return chain.process(ctx, r);
    }
}

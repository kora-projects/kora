package ru.tinkoff.kora.http.client.async;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import jakarta.annotation.Nullable;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.uri.Uri;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.*;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.util.concurrent.*;

public class AsyncHttpClient implements HttpClient, Lifecycle {
    private final org.asynchttpclient.AsyncHttpClient client;

    public AsyncHttpClient(org.asynchttpclient.AsyncHttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<HttpClientResponse> execute(HttpClientRequest request) {
        var ctx = Context.current();
        return this.processRequest(ctx, request)
            .exceptionallyCompose(e -> {
                if (e instanceof CompletionException ce) {
                    e = ce.getCause();
                }
                if (e instanceof IOException io) {
                    return CompletableFuture.failedFuture(new HttpClientConnectionException(io));
                }
                if (e instanceof TimeoutException timeout) {
                    return CompletableFuture.failedFuture(new HttpClientTimeoutException(timeout));
                }
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    return CompletableFuture.failedFuture(new HttpClientConnectionException(illegalArgumentException));
                }
                if (e instanceof HttpClientException clientException) {
                    return CompletableFuture.failedFuture(clientException);
                }
                return CompletableFuture.failedFuture(new HttpClientUnknownException(e));
            });
    }

    private CompletionStage<HttpClientResponse> processRequest(Context context, HttpClientRequest request) {
        var clientHeaders = new DefaultHttpHeaders();
        for (var header : request.headers()) {
            clientHeaders.add(header.getKey(), header.getValue());
        }
        var uri = new Uri(
            request.uri().getScheme(),
            request.uri().getRawUserInfo(),
            request.uri().getHost(),
            request.uri().getPort(),
            request.uri().getRawPath(),
            request.uri().getRawQuery(),
            request.uri().getRawFragment()
        );
        var requestBuilder = new RequestBuilder(request.method())
            .setUri(uri)
            .setHeaders(clientHeaders);
        if (request.requestTimeout() != null) {
            requestBuilder.setRequestTimeout((int) request.requestTimeout().toMillis());
        }
        try {
            this.setBody(requestBuilder, request.body(), context);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }

        var future = new CompletableFuture<HttpClientResponse>();
        var response = this.client.executeRequest(requestBuilder, new MonoSinkStreamAsyncHandler(context, future));

        return future.whenComplete((rs, error) -> {
            if (error instanceof CancellationException) {
                response.cancel(true);
            }
        });
    }

    private void setBody(RequestBuilder requestBuilder, @Nullable HttpBodyOutput body, Context context) throws Exception {
        if (body == null) {
            return;
        }
        if (body.contentType() != null) {
            requestBuilder.setHeader("content-type", body.contentType());
        }
        if (body.contentLength() == 0) {
            return;
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            requestBuilder.setBody(full);
            return;
        }
        requestBuilder.setBody(new AsyncHttpClientRequestBodyGenerator(body));
    }

    @Override
    public void init() {
    }

    @Override
    public void release() throws IOException {
        this.client.close();
    }
}

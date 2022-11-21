package ru.tinkoff.kora.http.client.async;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.uri.Uri;
import reactor.core.Exceptions;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientConnectionException;
import ru.tinkoff.kora.http.client.common.HttpClientTimeoutException;
import ru.tinkoff.kora.http.client.common.UnknownHttpClientException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncHttpClient implements HttpClient, Lifecycle {
    private final org.asynchttpclient.AsyncHttpClient client;

    public AsyncHttpClient(org.asynchttpclient.AsyncHttpClient client) {
        this.client = client;
    }

    @Override
    public Mono<HttpClientResponse> execute(HttpClientRequest request) {
        return Mono.deferContextual(ctx -> {
            var forkedContext = Context.Reactor.current(ctx).fork(); // we can modify whatever we want now
            return this.processRequest(forkedContext, request).contextWrite(c -> Context.Reactor.inject(c, forkedContext))
                .onErrorMap(e -> {
                    if (e instanceof IOException io) {
                        return new HttpClientConnectionException(io);
                    }
                    if (e instanceof TimeoutException timeout) {
                        return new HttpClientTimeoutException(timeout);
                    }
                    if (e instanceof IllegalArgumentException illegalArgumentException) {
                        return new HttpClientConnectionException(illegalArgumentException);
                    }
                    return new UnknownHttpClientException(e);
                });
        });
    }

    private Mono<HttpClientResponse> processRequest(Context context, HttpClientRequest request) {
        return Mono.create(sink -> {
            var clientHeaders = new DefaultHttpHeaders();
            for (var header : request.headers()) {
                clientHeaders.add(header.getKey(), header.getValue());
            }
            var uri = Uri.create(request.resolvedUri());
            var requestBuilder = new RequestBuilder(request.method())
                .setUri(uri)
                .setHeaders(clientHeaders);
            if (request.requestTimeout() > 0) {
                requestBuilder.setRequestTimeout(request.requestTimeout());
            }

            var subscribed = new AtomicBoolean(false);
            sink.onRequest(l -> {
                if (!subscribed.compareAndSet(false, true)) {
                    return;
                }
                try {
                    this.setBody(requestBuilder, request.body(), context);
                } catch (Exception exception) {
                    sink.error(exception);
                    return;
                }
                var response = this.client.executeRequest(requestBuilder, new MonoSinkStreamAsyncHandler(context, sink));
                sink.onCancel(() -> response.cancel(false));
            });
        });
    }

    private void setBody(RequestBuilder requestBuilder, Flux<ByteBuffer> body, Context context) throws Exception {
        if (body instanceof Fuseable.ScalarCallable<?> scalarCallable) {
            var value = (ByteBuffer) scalarCallable.call();
            requestBuilder.setBody(value);
            return;
        }
        var wrapped = Flux.deferContextual(ctx -> {
            // AsyncHttpClient is subscribing to body in different thread if there's no active connection, so our context is lost here
            return body.map(Unpooled::wrappedBuffer).contextWrite(c -> Context.Reactor.inject(c, context));
        });
        requestBuilder.setBody(wrapped);
    }

    @Override
    public Mono<Void> init() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> release() {
        return Mono.fromRunnable(() -> {
            try {
                this.client.close();
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            }
        });
    }
}

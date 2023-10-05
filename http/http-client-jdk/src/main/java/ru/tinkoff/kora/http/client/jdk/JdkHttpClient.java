package ru.tinkoff.kora.http.client.jdk;

import ru.tinkoff.kora.http.client.common.*;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

public class JdkHttpClient implements HttpClient {
    private final java.net.http.HttpClient httpClient;

    public JdkHttpClient(java.net.http.HttpClient client) {
        this.httpClient = client;
    }

    @Override
    public CompletionStage<HttpClientResponse> execute(HttpClientRequest request) {
        var httpClientRequest = HttpRequest.newBuilder()
            .uri(URI.create(request.resolvedUri()));
        if (request.requestTimeout() != null) {
            httpClientRequest.timeout(request.requestTimeout());
        }
        for (var header : request.headers()) {
            if (header.getKey().equalsIgnoreCase("content-length")) {
                continue;
            }
            if (header.getKey().equalsIgnoreCase("content-type")) {
                continue;
            }
            for (var value : header.getValue()) {
                httpClientRequest.header(header.getKey(), value);
            }
        }
        if (request.body().contentType() != null) {
            httpClientRequest.header("content-type", request.body().contentType());
        }
        httpClientRequest.method(request.method(), this.toBodyPublisher(request.body()));
        return this.httpClient.sendAsync(httpClientRequest.build(), HttpResponse.BodyHandlers.ofPublisher())
            .exceptionallyCompose(error -> {
                if (!(error instanceof CompletionException completionException) || !(completionException.getCause() instanceof IOException ioException)) {
                    return CompletableFuture.failedFuture(error);
                }
                if (ioException instanceof ProtocolException) {
                    return CompletableFuture.failedFuture(error);
                }
                if (ioException instanceof java.net.http.HttpTimeoutException) {
                    return CompletableFuture.failedFuture(error);
                }
                return this.httpClient.sendAsync(httpClientRequest.build(), HttpResponse.BodyHandlers.ofPublisher());
            })
            .exceptionallyCompose(error -> {
                if (error instanceof CompletionException completionException) {
                    error = completionException.getCause();
                }
                if (error instanceof java.net.ProtocolException protocolException) {
                    return CompletableFuture.failedFuture(new HttpClientConnectionException(protocolException));
                }
                if (error instanceof java.net.http.HttpConnectTimeoutException timeoutException) {
                    return CompletableFuture.failedFuture(new ru.tinkoff.kora.http.client.common.HttpClientConnectionException(timeoutException));
                }
                if (error instanceof java.net.http.HttpTimeoutException timeoutException) {
                    return CompletableFuture.failedFuture(new HttpClientTimeoutException(timeoutException));
                }
                if (error instanceof HttpClientException httpClientException) {
                    return CompletableFuture.failedFuture(httpClientException);
                }
                return CompletableFuture.failedFuture(new UnknownHttpClientException(error));
            })
            .thenApply(JdkHttpClientResponse::new);
    }

    private HttpRequest.BodyPublisher toBodyPublisher(HttpBodyOutput body) {
        if (body.contentLength() == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            if (full.remaining() == 0) {
                return HttpRequest.BodyPublishers.noBody();
            }
            if (full.hasArray()) {
                return HttpRequest.BodyPublishers.ofByteArray(full.array(), full.arrayOffset(), full.remaining());
            } else {
                return new JdkByteBufferBodyPublisher(full);
            }
        }
        if (body.contentLength() > 0) {
            return HttpRequest.BodyPublishers.fromPublisher(wrapRequestBOdyException(body), body.contentLength());
        } else {
            return HttpRequest.BodyPublishers.fromPublisher(wrapRequestBOdyException(body));
        }
    }

    private Flow.Publisher<? extends ByteBuffer> wrapRequestBOdyException(HttpBodyOutput body) {
        return subscriber -> body.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(new HttpClientEncoderException(throwable));
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

}

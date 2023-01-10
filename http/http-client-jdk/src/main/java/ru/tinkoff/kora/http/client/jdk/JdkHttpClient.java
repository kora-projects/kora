package ru.tinkoff.kora.http.client.jdk;

import reactor.adapter.JdkFlowAdapter;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.HttpClientConnectionException;
import ru.tinkoff.kora.http.client.common.HttpClientTimeoutException;
import ru.tinkoff.kora.http.client.common.UnknownHttpClientException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.BlockingHttpResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class JdkHttpClient implements HttpClient {
    private final java.net.http.HttpClient httpClient;

    public JdkHttpClient(java.net.http.HttpClient client) {
        this.httpClient = client;
    }

    @Override
    public Mono<HttpClientResponse> execute(HttpClientRequest request) {
        return Mono.deferContextual(ctxView -> {
            var httpClientRequest = HttpRequest.newBuilder()
                .uri(URI.create(request.resolvedUri()));
            if (request.requestTimeout() > 0) {
                httpClientRequest.timeout(Duration.ofMillis(request.requestTimeout()));
            }
            for (var header : request.headers()) {
                if (header.getKey().equalsIgnoreCase("content-length")) {
                    continue;
                }
                for (var value : header.getValue()) {
                    httpClientRequest.header(header.getKey(), value);
                }
            }
            httpClientRequest.method(request.method(), this.toBodyPublisher(request.body()));

            var future = this.httpClient.sendAsync(httpClientRequest.build(), HttpResponse.BodyHandlers.ofPublisher())
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
                });
            return Mono.fromFuture(future)
                .onErrorMap(error -> {
                    if (error instanceof java.net.ProtocolException protocolException) {
                        return new HttpClientConnectionException(protocolException);
                    }
                    if (error instanceof java.net.http.HttpConnectTimeoutException timeoutException) {
                        return new ru.tinkoff.kora.http.client.common.HttpClientConnectionException(timeoutException);
                    }
                    if (error instanceof java.net.http.HttpTimeoutException timeoutException) {
                        return new HttpClientTimeoutException(timeoutException);
                    }
                    return new UnknownHttpClientException(error);
                })
                .map(JdkHttpClientResponse::new);
        });
    }

    public BlockingHttpResponse executeBlocking(HttpClientRequest request) {
        var httpClientRequest = HttpRequest.newBuilder()
            .uri(URI.create(request.resolvedUri()));
        if (request.requestTimeout() > 0) {
            httpClientRequest.timeout(Duration.ofMillis(request.requestTimeout()));
        }
        for (var header : request.headers()) {
            if (header.getKey().equalsIgnoreCase("content-length")) {
                continue;
            }
            for (var value : header.getValue()) {
                httpClientRequest.header(header.getKey(), value);
            }
        }
        httpClientRequest.method(request.method(), this.toBodyPublisher(request.body()));

        try {
            var response = this.httpClient.send(httpClientRequest.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new JdkBlockingHttpResponse(response);
        } catch (ProtocolException | HttpConnectTimeoutException e) {
            throw new HttpClientConnectionException(e);
        } catch (HttpTimeoutException e) {
            throw new HttpClientTimeoutException(e);
        } catch (IOException | InterruptedException e) {
            throw new UnknownHttpClientException(e);
        }
    }

    private HttpRequest.BodyPublisher toBodyPublisher(Flux<ByteBuffer> body) {
        if (body instanceof Fuseable.ScalarCallable<?> callable) {
            ByteBuffer buf = null;
            try {
                buf = (ByteBuffer) callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (buf == null || buf.remaining() == 0) {
                return new JdkEmptyBodyPublisher();
            } else {
                return new JdkByteBufferBodyPublisher(buf);
            }
        }
        return HttpRequest.BodyPublishers.fromPublisher(JdkFlowAdapter.publisherToFlowPublisher(body));
    }

}

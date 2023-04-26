package ru.tinkoff.kora.http.client.common.telemetry;

import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DefaultHttpClientTelemetry implements HttpClientTelemetry {
    @Nullable
    private final HttpClientTracer tracing;
    @Nullable
    private final HttpClientMetrics metrics;
    @Nullable
    private final HttpClientLogger logger;

    public DefaultHttpClientTelemetry(@Nullable HttpClientTracer tracing, @Nullable HttpClientMetrics metrics, @Nullable HttpClientLogger logger) {
        this.tracing = tracing;
        this.metrics = metrics;
        this.logger = logger;
    }

    @Override
    public boolean isEnabled() {
        return metrics != null
               || tracing != null
               || logger != null && (logger.logRequest() || logger.logResponse());
    }

    @Override
    @Nullable
    public HttpServerTelemetryContext get(Context ctx, HttpClientRequest request) {
        var logger = this.logger;
        var tracing = this.tracing;
        var metrics = this.metrics;
        if (metrics == null && tracing == null && (logger == null || (!logger.logRequest() && !logger.logResponse()))) {
            return null;
        }

        var startTime = System.nanoTime();
        var method = request.method();
        var uri = URI.create(request.resolvedUri());
        var host = uri.getHost();
        var scheme = uri.getScheme();
        var operation = request.operation();
        var authority = request.authority();
        var resolvedUri = request.resolvedUri();
        var target = operation.substring(method.length() + 1);

        var createSpanResult = tracing == null ? null : tracing.createSpan(ctx, request);
        if (createSpanResult != null) {
            request = createSpanResult.request();
        }
        var headers = request.headers();

        if (logger != null && logger.logRequest()) {
            if (!logger.logRequestHeaders()) {
                logger.logRequest(authority, request.method(), operation, resolvedUri, null, null);
            } else {
                if (!logger.logRequestBody()) {
                    logger.logRequest(authority, request.method(), operation, resolvedUri, headers, null);
                } else {
                    var requestBodyCharset = this.detectCharset(request.headers());
                    if (requestBodyCharset == null) {
                        this.logger.logRequest(authority, request.method(), operation, resolvedUri, headers, null);
                    } else {
                        var requestBodyFlux = this.wrapBody(request.body(), true, l -> {
                            var s = byteBufListToBodyString(l, requestBodyCharset);
                            this.logger.logRequest(authority, method, operation, resolvedUri, headers, s);
                        }, e -> {}, () -> {});
                        request = request.toBuilder()
                            .body(requestBodyFlux)
                            .build();
                    }
                }
            }
        }
        var fRequest = request;
        return new HttpServerTelemetryContext() {
            @Override
            public HttpClientRequest request() {
                return fRequest;
            }

            @Override
            public HttpClientResponse close(@Nullable HttpClientResponse response, @Nullable Throwable exception) {
                if (response == null) {
                    if (createSpanResult != null) createSpanResult.span().close(exception);
                    var processingTime = System.nanoTime() - startTime;
                    if (metrics != null) {
                        metrics.record(-1, processingTime, method, host, scheme, target);
                    }
                    if (logger != null && logger.logResponse()) logger.logResponse(authority, operation, processingTime, null, HttpResultCode.CONNECTION_ERROR, exception, null, null);
                    return null;
                }
                var responseBodyCharset = logger == null || !logger.logResponseBody() ? null : detectCharset(response.headers());
                var bodySubscribed = new AtomicBoolean(false);
                var responseBodyFlux = wrapBody(response.body(), responseBodyCharset != null, l -> {
                    if (createSpanResult != null) createSpanResult.span().close(null);
                    var processingTime = System.nanoTime() - startTime;
                    if (metrics != null) {
                        metrics.record(response.code(), processingTime, method, host, scheme, target);
                    }
                    var resultCode = HttpResultCode.fromStatusCode(response.code());
                    if (logger != null) {
                        var headers = logger.logResponseHeaders() ? response.headers() : null;
                        var bodyString = byteBufListToBodyString(l, responseBodyCharset);
                        logger.logResponse(authority, operation, processingTime, response.code(), resultCode, null, headers, bodyString);
                    }
                }, e -> {
                    if (createSpanResult != null) createSpanResult.span().close(e);
                    var processingTime = System.nanoTime() - startTime;
                    if (metrics != null) {
                        metrics.record(-1, processingTime, method, host, scheme, target);
                    }
                    if (logger != null && logger.logResponse()) logger.logResponse(authority, operation, processingTime, null, HttpResultCode.CONNECTION_ERROR, e, null, null);
                }, () -> bodySubscribed.set(true));

                return new HttpClientResponse.Default(
                    response.code(),
                    response.headers(),
                    responseBodyFlux,
                    Mono.defer(() -> {
                        if (bodySubscribed.compareAndSet(false, true)) {
                            responseBodyFlux.subscribe();
                        }
                        return response.close();
                    })
                );
            }
        };
    }

    private static String byteBufListToBodyString(@Nullable List<ByteBuffer> l, Charset charset) {
        if (l == null || l.isEmpty()) {
            return null;
        }
        var sbl = 0;
        for (var byteBuffer : l) {
            sbl += byteBuffer.remaining();
        }
        var sb = new StringBuilder(sbl);
        for (var byteBuffer : l) {
            var cb = charset.decode(byteBuffer);
            sb.append(cb);
        }
        return sb.toString();
    }

    private Flux<ByteBuffer> wrapBody(Flux<ByteBuffer> body, boolean collectChunks, Consumer<List<ByteBuffer>> onComplete, Consumer<Throwable> onError, Runnable onSubscribe) {
        if (body instanceof Fuseable.ScalarCallable<?> callable) {
            try {
                var bytes = (ByteBuffer) callable.call();
                if (bytes == null) {
                    onComplete.accept(null);
                    return Flux.empty();
                } else {
                    onComplete.accept(collectChunks ? List.of(bytes.slice()) : null);
                    return Flux.just(bytes);
                }
            } catch (Exception e) {
                onError.accept(e);
                return Flux.error(e);
            }
        }

        var bodyChunks = collectChunks ? new ArrayList<ByteBuffer>() : null;
        return body.doOnSubscribe(s -> onSubscribe.run())
            .materialize()
            .<Signal<ByteBuffer>>handle((signal, sink) -> {
                if (signal.isOnComplete()) {
                    onComplete.accept(bodyChunks);
                } else if (signal.isOnError()) {
                    onError.accept(signal.getThrowable());
                } else if (collectChunks && signal.isOnNext()) {
                    bodyChunks.add(signal.get().slice());
                }
                sink.next(signal);
            })
            .dematerialize();
    }

    @Nullable
    private Charset detectCharset(HttpHeaders headers) {
        var contentType = headers.getFirst("content-type");
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        var split = contentType.split("; charset=", 2);
        if (split.length == 2) {
            return Charset.forName(split[1]);
        }
        var mimeType = split[0];
        if (mimeType.contains("text") || mimeType.contains("json")) {
            return StandardCharsets.UTF_8;
        }
        if (mimeType.contains("application/x-www-form-urlencoded")) {
            return StandardCharsets.US_ASCII;
        }
        return null;
    }
}

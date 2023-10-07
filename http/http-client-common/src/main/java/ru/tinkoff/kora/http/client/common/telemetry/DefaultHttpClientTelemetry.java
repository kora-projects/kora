package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
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
            || logger != null && (logger.logRequest() || logger.logRequestBody() || logger.logResponse() || logger.logResponseBody());
    }

    record TelemetryContextData(long startTime, String method, String operation, String host, String scheme, String authority, String target) {
        public TelemetryContextData(HttpClientRequest request) {
            this(
                System.nanoTime(),
                request.method(),
                DefaultHttpClientTelemetry.operation(request.method(), request.uriTemplate(), request.uri()),
                request.uri().getHost(),
                request.uri().getScheme(),
                request.uri().getAuthority(),
                request.uri().getPath()
            );
        }
    }

    private static String operation(String method, String uriTemplate, URI uri) {
        if (uri.getAuthority() != null) {
            if (uri.getScheme() != null) {
                uriTemplate = uriTemplate.replace(uri.getScheme() + "://" + uri.getAuthority(), "");
            }
        }
        var questionMark = uriTemplate.indexOf('?');
        if (questionMark >= 0) {
            uriTemplate = uriTemplate.substring(0, questionMark);
        }
        return method + " " + uriTemplate;
    }

    @Override
    @Nullable
    public HttpClientTelemetryContext get(Context ctx, HttpClientRequest request) {
        if (!this.isEnabled()) {
            return null;
        }
        var data = new TelemetryContextData(request);

        var method = request.method();
        var operation = data.operation();
        var authority = data.authority();
        var resolvedUri = request.uri().toString();

        var createSpanResult = tracing == null ? null : tracing.createSpan(ctx, request);
        var headers = request.headers();

        if (logger != null && logger.logRequest()) {
            if (!logger.logRequestHeaders()) {
                logger.logRequest(authority, request.method(), operation, resolvedUri, null, null);
            } else if (!logger.logRequestBody()) {
                logger.logRequest(authority, request.method(), operation, resolvedUri, headers, null);
            } else {
                var requestBodyCharset = this.detectCharset(request.body().contentType());
                if (requestBodyCharset == null) {
                    this.logger.logRequest(authority, request.method(), operation, resolvedUri, headers, null);
                } else {
                    var requestBodyFlux = this.wrapRequestBody(ctx, request.body(), buffers -> {
                        var bodyString = byteBufListToBodyString(buffers, requestBodyCharset);
                        this.logger.logRequest(authority, method, operation, resolvedUri, headers, bodyString);
                    });
                    request = request.toBuilder()
                        .body(HttpBodyOutput.of(request.body().contentType(), requestBodyFlux))
                        .build();
                }
            }
        }
        return new DefaultHttpClientTelemetryContextImpl(ctx, request, data, createSpanResult, metrics, logger);
    }

    private static String byteBufListToBodyString(@Nullable List<ByteBuffer> l, @Nullable Charset charset) {
        if (l == null || l.isEmpty() || charset == null) {
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

    private Flow.Publisher<ByteBuffer> wrapRequestBody(Context ctx, HttpBodyOutput body, Consumer<List<ByteBuffer>> onComplete) {
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            try {
                onComplete.accept(List.of(full.slice()));
            } catch (Exception ignore) {
            }
            return FlowUtils.one(ctx, full);
        }
        var bodyChunks = new ArrayList<ByteBuffer>();
        return subscriber -> body.subscribe(new Flow.Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(ByteBuffer item) {
                bodyChunks.add(item);
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                onComplete.accept(bodyChunks);
                subscriber.onComplete();
            }
        });
    }

    @Nullable
    private Charset detectCharset(String contentType) {
        if (contentType == null) {
            return null;
        }
        var split = contentType.split("; charset=", 2);
        if (split.length == 2) {
            return Charset.forName(split[1]);
        }
        var mimeType = split[0];
        if (mimeType.contains("text") || mimeType.contains("json") || mimeType.contains("xml")) {
            return StandardCharsets.UTF_8;
        }
        if (mimeType.contains("application/x-www-form-urlencoded")) {
            return StandardCharsets.US_ASCII;
        }
        return null;
    }

    public class DefaultHttpClientTelemetryContextImpl implements HttpClientTelemetryContext {
        private final Context ctx;
        private final HttpClientRequest request;
        private final TelemetryContextData data;
        private final HttpClientTracer.HttpClientSpan span;
        private final HttpClientMetrics metrics;
        private final HttpClientLogger logger;

        public DefaultHttpClientTelemetryContextImpl(Context ctx, HttpClientRequest request, TelemetryContextData data, HttpClientTracer.HttpClientSpan span, HttpClientMetrics metrics, HttpClientLogger logger) {
            this.ctx = ctx;
            this.request = request;
            this.data = data;
            this.span = span;
            this.metrics = metrics;
            this.logger = logger;
        }

        @Override
        public HttpClientRequest request() {
            return request;
        }

        @Override
        public HttpClientResponse close(@Nullable HttpClientResponse response, @Nullable Throwable exception) {
            if (response == null) {
                this.onClose(exception);
                return null;
            }

            var full = response.body().getFullContentIfAvailable();
            if (full != null) {
                this.onClose(response.code(), response.headers(), response.body().contentType(), List.of(full));
                return response;
            }
            var responseBodyCharset = logger == null || !logger.logResponseBody() ? null : detectCharset(response.body().contentType());
            if (responseBodyCharset != null) {
                var body = new DefaultHttpClientTelemetryCollectingResponseBodyWrapper(response, this);
                return new DefaultHttpClientTelemetryResponseWrapper(response, body);
            } else {
                var body = new DefaultHttpClientTelemetryResponseBodyWrapper(response, this);
                return new DefaultHttpClientTelemetryResponseWrapper(response, body);
            }
        }

        public void onClose(Throwable throwable) {
            if (span != null) span.close(throwable);
            var processingTime = System.nanoTime() - data.startTime();
            if (metrics != null) {
                metrics.record(-1, processingTime, request.method(), data.host(), data.scheme(), data.target());
            }
            if (logger != null && logger.logResponse()) {
                try {
                    this.ctx.inject();
                    logger.logResponse(data.authority(), data.operation(), processingTime, null, HttpResultCode.CONNECTION_ERROR, throwable, null, null);
                } finally {
                    ctx.inject();
                }
            }
        }

        public void onClose(int code, @Nullable HttpHeaders headers, @Nullable String contentType, @Nullable List<ByteBuffer> body) {
            var responseBodyCharset = logger == null || !logger.logResponseBody() ? null : detectCharset(contentType);
            if (span != null) {
                span.close(null);
            }
            var processingTime = System.nanoTime() - data.startTime();
            if (metrics != null) {
                metrics.record(code, processingTime, request.method(), data.host(), data.scheme(), data.target());
            }
            var resultCode = HttpResultCode.fromStatusCode(code);
            if (logger != null) {
                var headersStr = logger.logResponseHeaders() ? headers : null;
                var bodyString = byteBufListToBodyString(body, responseBodyCharset);
                var ctx = Context.current();
                try {
                    this.ctx.inject();
                    logger.logResponse(data.authority(), data.operation(), processingTime, code, resultCode, null, headersStr, bodyString);
                } finally {
                    ctx.inject();
                }
            }
        }
    }
}

package ru.tinkoff.kora.http.client.common.telemetry.impl;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientObservation;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;
import ru.tinkoff.kora.http.common.HttpResultCode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DefaultHttpClientObservation implements HttpClientObservation {
    private final HttpClientTelemetryConfig config;
    private final DefaultHttpClientLogger logger;
    private final DefaultHttpClientMetrics metrics;
    private final HttpClientRequest rq;
    private final Span span;

    private int statusCode = -1;
    private final long startNanos = System.nanoTime();

    @Nullable
    private HttpClientResponse rs;
    @Nullable
    private Throwable exception;
    private HttpResultCode resultCode;
    private long processingTime;

    public DefaultHttpClientObservation(HttpClientTelemetryConfig config, DefaultHttpClientLogger logger, DefaultHttpClientMetrics metrics, HttpClientRequest rq, Span span) {
        this.config = config;
        this.logger = logger;
        this.metrics = metrics;
        this.rq = rq;
        this.span = span;
    }

    @Override
    public HttpClientRequest observeRequest(HttpClientRequest rq) {
        if (logger.logRequestBody()) {
            var charset = detectCharset(rq.body().contentType());
            if (charset != null) {
                var body = rq.body();
                var full = body.getFullContentIfAvailable();
                if (full != null) {
                    logger.logRequest(rq, charset.decode(full).toString());
                    return rq;
                }

                return rq.toBuilder()
                    .body(new DefaultHttpClientTelemetryRequestBodyWrapper(rq, body, charset, logger))
                    .build();
            }
        }
        logger.logRequest(rq, null);
        return rq;
    }

    @Override
    public HttpClientResponse observeResponse(HttpClientResponse rs) {
        this.statusCode = rs.code();
        this.rs = rs;
        this.resultCode = HttpResultCode.fromStatusCode(rs.code());
        this.processingTime = System.nanoTime() - startNanos;
        if (logger.logResponseBody()) {
            var charset = detectCharset(rs.body().contentType());
            if (charset != null) {
                var body = rs.body();
                var full = body.getFullContentIfAvailable();
                if (full != null) {
                    logger.logResponse(rq, rs, processingTime, charset.decode(full).toString());
                    return rs;
                }
                var rsBody = new DefaultHttpClientTelemetryCollectingResponseBodyWrapper(logger, rq, rs, processingTime, charset);
                return new DefaultHttpClientTelemetryResponseWrapper(rs, rsBody);
            }
        }
        logger.logResponse(rq, rs, processingTime, null);
        return rs;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        this.recordMetrics();
        this.completeSpan();
    }

    @Override
    public void observeError(Throwable e) {
        this.exception = e;
        this.span.recordException(e);
        this.processingTime = System.nanoTime() - startNanos;
        this.resultCode = HttpResultCode.CONNECTION_ERROR;
        logger.logError(rq, processingTime, exception);
    }

    protected void recordMetrics() {
        if (rs == null) {
            this.metrics.recordFailure(rq, Objects.requireNonNull(exception), processingTime);
        } else {
            this.metrics.recordSuccess(rq, Objects.requireNonNull(rs), processingTime);
        }
    }

    protected void completeSpan() {
        var end = System.nanoTime();
        var resultCode = Objects.requireNonNullElse(this.resultCode, HttpResultCode.SERVER_ERROR);
        if (statusCode >= 500 || resultCode == HttpResultCode.CONNECTION_ERROR) {
            span.setStatus(StatusCode.ERROR);
        } else if (exception == null) {
            span.setStatus(StatusCode.OK);
        }
        span.setAttribute("http.response.result_code", resultCode.string());
        if (statusCode != 0) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
        }
        span.end(end, TimeUnit.NANOSECONDS);
    }

    @Nullable
    protected Charset detectCharset(String contentType) {
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

}

package io.koraframework.http.client.common.telemetry.impl;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import org.jspecify.annotations.Nullable;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientObservation;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.common.HttpResultCode;

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
    public HttpClientRequest observeRequest(HttpClientRequest request) {
        if (logger.logRequestBody()) {
            var charset = detectCharset(request.body().contentType());
            if (charset != null) {
                var body = request.body();
                var full = body.getFullContentIfAvailable();
                if (full != null) {
                    logger.logRequest(request, charset.decode(full).toString());
                    return request;
                }

                return request.toBuilder()
                    .body(new DefaultHttpClientTelemetryRequestBodyWrapper(request, body, charset, logger))
                    .build();
            }
        }
        logger.logRequest(request, null);
        return request;
    }

    @Override
    public HttpClientResponse observeResponse(HttpClientResponse response) {
        this.statusCode = response.code();
        this.rs = response;
        this.resultCode = HttpResultCode.fromStatusCode(response.code());
        this.processingTime = System.nanoTime() - startNanos;
        if (logger.logResponseBody()) {
            var charset = detectCharset(response.body().contentType());
            if (charset != null) {
                var body = response.body();
                var full = body.getFullContentIfAvailable();
                if (full != null) {
                    logger.logResponse(rq, response, processingTime, charset.decode(full).toString());
                    return response;
                }
                var rsBody = new DefaultHttpClientTelemetryCollectingResponseBodyWrapper(logger, rq, response, processingTime, charset);
                return new DefaultHttpClientTelemetryResponseWrapper(response, rsBody);
            }
        }
        logger.logResponse(rq, response, processingTime, null);
        return response;
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
        if (statusCode >= 400 || resultCode == HttpResultCode.CONNECTION_ERROR || exception != null) {
            span.setStatus(StatusCode.ERROR);
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

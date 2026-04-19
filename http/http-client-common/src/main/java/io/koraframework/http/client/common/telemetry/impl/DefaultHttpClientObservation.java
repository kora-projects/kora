package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientObservation;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.EmptyHttpBody;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DefaultHttpClientObservation implements HttpClientObservation {

    protected final HttpClientTelemetryConfig config;
    protected final DefaultHttpClientLogger logger;
    protected final DefaultHttpClientMetrics metrics;
    protected final HttpClientRequest request;
    protected final Span span;

    protected final long startNanos = System.nanoTime();

    @Nullable
    protected HttpClientResponse response;
    @Nullable
    protected Throwable exception;

    protected int statusCode = -1;
    protected HttpResultCode resultCode;
    protected long processingTookNanos;

    public DefaultHttpClientObservation(HttpClientTelemetryConfig config,
                                        DefaultHttpClientLogger logger,
                                        DefaultHttpClientMetrics metrics,
                                        HttpClientRequest request,
                                        Span span) {
        this.config = config;
        this.logger = logger;
        this.metrics = metrics;
        this.request = request;
        this.span = span;
    }

    @Override
    public HttpClientRequest observeRequest(HttpClientRequest request) {
        if (logger.logRequestBody()) {
            var body = request.body();
            if (EmptyHttpBody.INSTANCE == body) {
                logger.logRequest(request, null, null);
                return request;
            }

            var full = body.getFullContentIfAvailable();
            if (full != null) {
                logger.logRequest(request, full, request.body().contentType());
                return request;
            }

            return request.toBuilder()
                .body(new DefaultHttpClientTelemetryRequestBodyWrapper(request, body, logger))
                .build();
        }

        logger.logRequest(request, null, request.body().contentType());
        return request;
    }

    @Override
    public HttpClientResponse observeResponse(HttpClientResponse response) {
        this.processingTookNanos = System.nanoTime() - startNanos;

        this.statusCode = response.code();
        this.response = response;
        this.resultCode = HttpResultCode.fromStatusCode(response.code());
        if (logger.logResponseBody()) {
            var body = response.body();
            var full = body.getFullContentIfAvailable();
            if (full != null) {
                logger.logResponse(request, response, processingTookNanos, full, response.body().contentType());
                return response;
            }

            var rsBody = new DefaultHttpClientTelemetryCollectingResponseBodyWrapper(logger, request, response, processingTookNanos);
            return new DefaultHttpClientTelemetryResponseWrapper(response, rsBody);
        }

        logger.logResponse(request, response, processingTookNanos, null, response.body().contentType());
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
        this.processingTookNanos = System.nanoTime() - startNanos;
        this.resultCode = HttpResultCode.CONNECTION_ERROR;

        this.logger.logError(request, processingTookNanos, exception);
    }

    protected void recordMetrics() {
        if (response == null) {
            this.metrics.recordFailure(request, exception, processingTookNanos);
        } else {
            this.metrics.recordSuccess(request, response, processingTookNanos);
        }
    }

    protected void completeSpan() {
        var end = System.nanoTime();
        var resultCode = Objects.requireNonNullElse(this.resultCode, HttpResultCode.SERVER_ERROR);
        if (statusCode >= 400 || resultCode == HttpResultCode.CONNECTION_ERROR || exception != null) {
            span.setStatus(StatusCode.ERROR);
        } else {
            span.setStatus(StatusCode.OK);
        }

        span.setAttribute("http.response.result_code", resultCode.string());
        if (statusCode != -1) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
        }
        span.end(end, TimeUnit.NANOSECONDS);
    }
}

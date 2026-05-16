package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.exception.HttpClientDecoderException;
import io.koraframework.http.client.common.exception.HttpClientEncoderException;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.SimpleHttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientObservation;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.EmptyHttpBody;
import io.koraframework.http.common.body.HttpBody;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DefaultHttpClientObservation implements HttpClientObservation {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpClientObservation.class);

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
        if (!logger.logRequestBody()) {
            logger.logRequest(request, null, request.body().contentType());
            return request;
        }

        var body = request.body();
        if (EmptyHttpBody.INSTANCE == body || body.contentLength() == 0L) {
            logger.logRequest(request, null, null);
            return request;
        }

        var full = body.getFullContentIfAvailable();
        if (full != null) {
            var lenInBytes = full.remaining();
            if (lenInBytes > config.logging().maxResponseBodyLogSize().toBytes()) {
                log.warn("Can't log request body bigger than {}, change config value if require logging, logging request without body...", config.logging().maxResponseBodyLogSize());
                logger.logRequest(request, null, body.contentType());
            } else {
                logger.logRequest(request, full, body.contentType());
            }
            full.rewind();
            return request;
        }

        // todo we better have some kind of config for max bytes to log and log part (and return input stream concatenation as body)
        var lenInBytes = body.contentLength();
        if (lenInBytes > config.logging().maxRequestBodyLogSize().toBytes()) {
            log.warn("Can't log request body bigger than {}, change config value if require logging, logging request without body...", config.logging().maxRequestBodyLogSize());
            logger.logRequest(request, null, body.contentType());
            return request;
        }

        try (body; var baos = new ByteArrayOutputStream(lenInBytes > 0 ? (int) lenInBytes : 1024)) {
            body.write(baos);

            var fullBody = baos.toByteArray();
            logger.logRequest(request, ByteBuffer.wrap(fullBody), body.contentType());

            return request.toBuilder()
                .body(HttpBody.of(body.contentType(), ByteBuffer.wrap(fullBody)))
                .build();
        } catch (IOException e) {
            throw new HttpClientEncoderException(e);
        }
    }

    @Override
    public HttpClientResponse observeResponse(HttpClientResponse response) {
        this.processingTookNanos = System.nanoTime() - startNanos;

        this.statusCode = response.code();
        this.response = response;
        this.resultCode = HttpResultCode.fromStatusCode(response.code());
        if (!logger.logResponseBody()) {
            logger.logResponse(request, response, processingTookNanos, null, response.body().contentType());
            return response;
        }

        var body = response.body();
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            var lenInBytes = full.remaining();
            if (lenInBytes > config.logging().maxResponseBodyLogSize().toBytes()) {
                log.warn("Can't log response body bigger than {}, change config value if require logging, logging response without body...", config.logging().maxResponseBodyLogSize());
                logger.logResponse(request, response, processingTookNanos, null, body.contentType());
            } else {
                logger.logResponse(request, response, processingTookNanos, full, body.contentType());
            }
            full.rewind();
            return response;
        }

        // todo we better have some kind of config for max bytes to log and log part (and return input stream concatenation as body)
        var lenInBytes = body.contentLength();
        if (lenInBytes > config.logging().maxResponseBodyLogSize().toBytes()) {
            log.warn("Can't log response body bigger than {}, change config value if require logging, now logging response without body...", config.logging().maxResponseBodyLogSize());
            logger.logResponse(request, response, processingTookNanos, null, body.contentType());
            return response;
        }

        try (response; body; var is = body.asInputStream()) {
            var bytes = is.readAllBytes();
            logger.logResponse(request, response, processingTookNanos, ByteBuffer.wrap(bytes), body.contentType());

            var bufferedBody = HttpBody.of(body.contentType(), ByteBuffer.wrap(bytes));
            return new SimpleHttpClientResponse(response.code(), response.headers(), bufferedBody);
        } catch (IOException e) {
            throw new HttpClientDecoderException(e);
        }
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

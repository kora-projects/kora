package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.EmptyHttpBody;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
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

public class DefaultHttpServerObservation implements HttpServerObservation {

    private static final Logger log = LoggerFactory.getLogger(DefaultHttpServerObservation.class);

    protected int statusCode = 0;
    @Nullable
    protected HttpResultCode resultCode;
    @Nullable
    protected HttpHeaders httpHeaders;
    @Nullable
    protected HttpServerResponse response;
    @Nullable
    protected ByteBuffer responseBody;
    @Nullable
    protected String responseContentType;
    @Nullable
    protected Throwable exception;
    protected final DefaultHttpServerTelemetry.TelemetryContext context;
    protected final HttpServerRequest request;
    protected final long requestStartTimeInNanos;
    protected final Span span;
    protected final DefaultHttpServerLoggerFactory.DefaultHttpServerLogger logger;
    protected final DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics;

    public DefaultHttpServerObservation(DefaultHttpServerTelemetry.TelemetryContext context,
                                        DefaultHttpServerLoggerFactory.DefaultHttpServerLogger logger,
                                        DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics,
                                        HttpServerRequest request,
                                        long requestStartTimeInNanos,
                                        Span span) {
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.request = request;
        this.requestStartTimeInNanos = requestStartTimeInNanos;
        this.span = span;
    }

    @Override
    public void observeResultCode(HttpResultCode resultCode) {
        this.resultCode = resultCode;
    }

    @Override
    public void observeError(Throwable exception) {
        this.exception = exception;
        this.span.recordException(exception);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public HttpServerRequest observeRequest(HttpServerRequest request) {
        this.metrics.recordStart(request);

        if (!logger.logRequestBody() || this.request.pathTemplate() == null) {
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
            if (lenInBytes > context.config().logging().maxRequestBodyLogSize().toBytes()) {
                log.warn("Can't log request body bigger than {}, change config value if require logging, logging request without body cause content length is {}...",
                    context.config().logging().maxRequestBodyLogSize(), lenInBytes);
                logger.logRequest(request, null, body.contentType());
            } else {
                logger.logRequest(request, full, body.contentType());
            }
            full.rewind();
            return request;
        }

        var lenInBytes = body.contentLength();
        if (lenInBytes > context.config().logging().maxRequestBodyLogSize().toBytes()) {
            log.warn("Can't log request body bigger than {}, change config value if require logging, logging request without body cause content length is {}...",
                context.config().logging().maxRequestBodyLogSize(), lenInBytes);
            logger.logRequest(request, null, body.contentType());
            return request;
        }

        try (body; var is = body.asInputStream()) {
            var bytes = is.readAllBytes();
            logger.logRequest(request, ByteBuffer.wrap(bytes), body.contentType());
            return request.toBuilder()
                .body(HttpBody.of(body.contentType(), ByteBuffer.wrap(bytes)))
                .build();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public HttpServerResponse observeResponse(HttpServerResponse response) {
        this.response = response;
        this.httpHeaders = response.headers();
        if (this.statusCode == 0) {
            this.resultCode = HttpResultCode.fromStatusCode(response.code());
        }
        this.statusCode = response.code();
        if (this.request.pathTemplate() == null) {
            return response;
        }

        var body = response.body();
        if (body == null || EmptyHttpBody.INSTANCE == body) {
            return response;
        }
        if (!logger.logResponseBody()) {
            return response;
        }

        var full = body.getFullContentIfAvailable();
        if (full != null) {
            var lenInBytes = full.remaining();
            if (lenInBytes > context.config().logging().maxResponseBodyLogSize().toBytes()) {
                log.warn("Can't log response body bigger than {}, change config value if require logging, logging response without body cause content length is {}...",
                    context.config().logging().maxResponseBodyLogSize(), lenInBytes);
            } else {
                this.responseBody = full;
                this.responseContentType = body.contentType();
            }
            full.rewind();
            return response;
        }

        var lenInBytes = body.contentLength();
        if (lenInBytes > context.config().logging().maxResponseBodyLogSize().toBytes()) {
            log.warn("Can't log response body bigger than {}, change config value if require logging, now logging response without body cause content length is {}...",
                context.config().logging().maxResponseBodyLogSize(), lenInBytes);
            return response;
        }

        try (body; var baos = new ByteArrayOutputStream(lenInBytes > 0 ? (int) lenInBytes : 1024)) {
            body.write(baos);
            var bytes = baos.toByteArray();
            this.responseBody = ByteBuffer.wrap(bytes);
            this.responseContentType = body.contentType();
            return HttpServerResponse.of(response.code(), response.headers(), HttpBody.of(body.contentType(), ByteBuffer.wrap(bytes)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var end = System.nanoTime();
        var processingTime = end - requestStartTimeInNanos;
        this.recordMetrics(processingTime);
        this.writeLog(processingTime);
        this.closeSpan(resultCode);
    }

    protected void recordMetrics(long processingTime) {
        var response = this.response != null
            ? this.response
            : HttpServerResponse.of(statusCode, httpHeaders);
        this.metrics.recordEnd(request, response, exception, processingTime);
    }

    protected void writeLog(long processingTime) {
        var response = this.response != null
            ? this.response
            : HttpServerResponse.of(statusCode, httpHeaders);
        this.logger.logResponse(request, response, Objects.requireNonNullElse(this.resultCode, HttpResultCode.SERVER_ERROR), processingTime, responseBody, responseContentType, exception);
    }

    protected void closeSpan(@Nullable HttpResultCode resultCode) {
        if (request.pathTemplate() != null) {
            resultCode = Objects.requireNonNullElse(resultCode, HttpResultCode.SERVER_ERROR);
            span.setAttribute("http.response.result_code", resultCode.string());
            if (statusCode >= 500 || resultCode == HttpResultCode.CONNECTION_ERROR || exception != null) {
                span.setStatus(StatusCode.ERROR);
            } else {
                span.setStatus(StatusCode.OK);
            }
            if (statusCode != 0) {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            }
            span.end();
        }
    }
}

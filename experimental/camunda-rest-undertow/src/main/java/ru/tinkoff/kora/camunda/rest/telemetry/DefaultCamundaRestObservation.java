package ru.tinkoff.kora.camunda.rest.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.http.server.common.telemetry.impl.DefaultHttpServerLogger;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class DefaultCamundaRestObservation implements CamundaRestObservation {

    protected int statusCode = 0;
    @Nullable
    protected HttpResultCode resultCode;
    @Nullable
    protected HttpHeaders httpHeaders;
    @Nullable
    protected Throwable exception;
    protected final HttpServerTelemetryConfig config;
    protected final long requestStartTime;
    protected final Span span;
    protected final DefaultHttpServerLogger logger;
    protected final Meter.MeterProvider<io.micrometer.core.instrument.Timer> requestDuration;
    protected Function<Tags, AtomicLong> activeRequests;

    public DefaultCamundaRestObservation(HttpServerTelemetryConfig config, long requestStartTime, Span span, DefaultHttpServerLogger logger, Meter.MeterProvider<Timer> requestDuration, Function<Tags, AtomicLong> activeRequests) {
        this.config = config;
        this.requestStartTime = requestStartTime;
        this.span = span;
        this.logger = logger;
        this.requestDuration = requestDuration;
        this.activeRequests = activeRequests;
    }

    @Override
    public void observeError(Throwable exception) {
        this.exception = exception;
        this.span.recordException(exception);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public HttpServerRequest observeRequest(HttpServerRequest rq) {
        var logger = this.logger;
        if (this.config.metrics().enabled()) {
            this.activeRequests.apply(Tags.empty()).decrementAndGet();
        }
        if (this.request.route() != null && this.config.logging().enabled()) {
            logger.logStart(request);
        }
        return rq;
    }

    @Override
    public void observeResponseCode(int code) {
        if (this.statusCode == 0) {
            this.resultCode = HttpResultCode.fromStatusCode(code);
        }
        this.statusCode = code;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var end = System.nanoTime();
        var processingTime = end - requestStartTime;
        this.recordMetrics(processingTime);
        this.writeLog(processingTime);
        this.closeSpan(resultCode);
    }

    protected void recordMetrics(long processingTime) {
        if (this.config.metrics().enabled()) {
            var tags = Tags.of(ErrorAttributes.ERROR_TYPE.getKey(), exception == null ? "" : exception.getClass().getCanonicalName());
            this.requestDuration.withTags(tags)
                .record(processingTime, TimeUnit.NANOSECONDS);
            this.activeRequests.apply(Tags.empty()).decrementAndGet();
        }
    }

    protected void writeLog(long processingTime) {
        if (request.route() != null && this.config.logging().enabled()) {
            this.logger.logEnd(request, statusCode, Objects.requireNonNullElse(this.resultCode, HttpResultCode.SERVER_ERROR), processingTime, httpHeaders, exception);
        }
    }

    protected void closeSpan(HttpResultCode resultCode) {
        if (request.route() != null) {
            span.setAttribute("http.response.result_code", resultCode.string());
            if (statusCode >= 500 || resultCode == HttpResultCode.CONNECTION_ERROR) {
                span.setStatus(StatusCode.ERROR);
            } else if (exception == null) {
                span.setStatus(StatusCode.OK);
            }
            if (statusCode != 0) {
                span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            }
            span.end();
        }
    }

}

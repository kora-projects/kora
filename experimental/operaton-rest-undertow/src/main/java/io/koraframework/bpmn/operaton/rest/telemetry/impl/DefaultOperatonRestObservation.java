package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestObservation;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.RouterHttpServerRequest;
import io.koraframework.http.server.undertow.request.UndertowHttpRouterRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.HttpAttributes;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

public class DefaultOperatonRestObservation implements OperatonRestObservation {

    protected int statusCode = 0;
    protected final HttpServerExchange exchange;
    @Nullable
    protected HttpResultCode resultCode;
    @Nullable
    protected HttpHeaders httpHeaders;
    @Nullable
    protected Throwable exception;
    protected final DefaultOperatonRestTelemetry.TelemetryContext context;
    protected final long requestStartTime;
    protected final Span span;
    protected final DefaultOperatonRestLoggerFactory.DefaultOperatonRestLogger logger;
    protected final DefaultOperatonRestMetricsFactory.DefaultOperatonRestMetrics metrics;
    @Nullable
    private String route;
    @Nullable
    private Map<String, String> pathParams;

    public DefaultOperatonRestObservation(HttpServerExchange exchange,
                                          DefaultOperatonRestTelemetry.TelemetryContext context,
                                          long requestStartTime,
                                          Span span,
                                          DefaultOperatonRestLoggerFactory.DefaultOperatonRestLogger logger,
                                          DefaultOperatonRestMetricsFactory.DefaultOperatonRestMetrics metrics) {
        this.exchange = exchange;
        this.context = context;
        this.requestStartTime = requestStartTime;
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public void observeError(Throwable exception) {
        this.exception = exception;
        this.span.recordException(exception);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void observeRequest(String route, Map<String, String> pathParams) {
        this.route = route;
        this.pathParams = pathParams;
        this.metrics.recordActive(this.exchange, route, 1);
        var request = new RouterHttpServerRequest(
            new UndertowHttpRouterRequest(this.exchange),
            pathParams,
            route
        );
        this.logger.logStart(request);
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
        this.metrics.recordDuration(this.exchange, this.route, this.statusCode, this.resultCode, this.exception, processingTime);
        if (this.route != null) {
            this.metrics.recordActive(this.exchange, this.route, -1);
        }
        this.writeLog(processingTime);
        this.closeSpan(resultCode);
    }

    protected void writeLog(long processingTime) {
        if (route != null) {
            var request = new RouterHttpServerRequest(
                new UndertowHttpRouterRequest(exchange),
                pathParams,
                route
            );
            this.logger.logEnd(request, statusCode, Objects.requireNonNullElse(this.resultCode, HttpResultCode.SERVER_ERROR), processingTime, httpHeaders, exception);
        }
    }

    protected void closeSpan(@Nullable HttpResultCode resultCode) {
        if (route != null) {
            var finalResultCode = Objects.requireNonNullElse(resultCode, HttpResultCode.SERVER_ERROR);
            span.setAttribute("http.response.result_code", finalResultCode.string());
            if (statusCode >= 500 || finalResultCode == HttpResultCode.CONNECTION_ERROR) {
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

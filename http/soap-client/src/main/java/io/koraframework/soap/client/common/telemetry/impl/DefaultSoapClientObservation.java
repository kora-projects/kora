package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.soap.client.common.SoapMethodDescriptor;
import io.koraframework.soap.client.common.SoapResult;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.envelope.SoapFault;
import io.koraframework.soap.client.common.telemetry.SoapClientObservation;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;

import java.util.concurrent.TimeUnit;

public class DefaultSoapClientObservation implements SoapClientObservation {

    protected static final AttributeKey<String> FAULT_CODE = AttributeKey.stringKey("fault.code");
    protected static final AttributeKey<String> FAULT_ACTOR = AttributeKey.stringKey("fault.actor");

    protected final long start = System.nanoTime();
    protected final DefaultSoapClientTelemetry.TelemetryContext context;
    protected final SoapMethodDescriptor descriptor;
    protected final Span span;
    protected final DefaultSoapClientLoggerFactory.DefaultSoapClientLogger logger;
    protected final DefaultSoapClientMetricsFactory.DefaultSoapClientMetrics metrics;

    protected final SoapEnvelope requestEnvelope;
    protected HttpClientResponse httpResponse;
    protected SoapResult.Failure failure;
    protected Throwable error;
    protected SoapFault fault;
    protected int httpCode = -1;

    public DefaultSoapClientObservation(SoapEnvelope requestEnvelope,
                                        DefaultSoapClientTelemetry.TelemetryContext context,
                                        Span span,
                                        DefaultSoapClientLoggerFactory.DefaultSoapClientLogger logger,
                                        DefaultSoapClientMetricsFactory.DefaultSoapClientMetrics metrics) {
        this.requestEnvelope = requestEnvelope;
        this.context = context;
        this.descriptor = context.descriptor();
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public Span span() {
        return span;
    }

    @Override
    public void observeRequest(SoapEnvelope requestEnvelope) {

    }

    @Override
    public void observeRequestXml(byte[] requestXml) {
        this.logger.logRequest(requestXml);
    }

    @Override
    public void observeHttpResponse(HttpClientResponse httpClientResponse) {
        this.httpResponse = httpClientResponse;
        this.httpCode = httpClientResponse.code();
        this.span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, httpClientResponse.code());
    }

    @Override
    public void observeResponseBody(byte[] resultXml) {
        this.logger.logResponse(resultXml);
    }

    @Override
    public void observeFailure(SoapResult.Failure result) {
        this.failure = result;
        this.fault = result.fault();
        this.span.setStatus(StatusCode.ERROR);
        this.span.setAttribute(FAULT_CODE, result.fault().getFaultcode().toString());
        this.span.setAttribute(FAULT_ACTOR, result.fault().getFaultactor());
        this.logger.logFailure(result);
    }

    @Override
    public void observeResult(Object body) {

    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.recordException(e);
        this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), e.getClass().getCanonicalName());
        this.span.setStatus(StatusCode.ERROR);
        this.logger.logError(e);
    }

    @Override
    public void end() {
        var took = System.nanoTime() - this.start;
        this.metrics.record(this.requestEnvelope, this.httpResponse, this.failure, this.error, took);
        if (this.error == null && this.fault == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end(System.nanoTime(), TimeUnit.NANOSECONDS);
    }
}

package ru.tinkoff.kora.soap.client.common.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import org.slf4j.Logger;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.soap.client.common.SoapMethodDescriptor;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.envelope.SoapFault;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class DefaultSoapClientObservation implements SoapClientObservation {
    protected static final AttributeKey<String> FAULT_CODE = AttributeKey.stringKey("fault.code");
    protected static final AttributeKey<String> FAULT_ACTOR = AttributeKey.stringKey("fault.actor");


    protected final long start = System.nanoTime();
    protected final SoapMethodDescriptor descriptor;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> duration;
    protected final Logger requestLog;
    protected final Logger responseLog;

    protected Throwable error;
    protected SoapFault fault;
    protected int httpCode;

    public DefaultSoapClientObservation(SoapMethodDescriptor descriptor, Span span, Meter.MeterProvider<Timer> duration, Logger requestLog, Logger responseLog) {
        this.descriptor = descriptor;
        this.span = span;
        this.duration = duration;
        this.requestLog = requestLog;
        this.responseLog = responseLog;
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
        requestLog
            .atInfo()
            .addKeyValue("soapRequest", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("soapMethod", descriptor.method);
                if (requestLog.isTraceEnabled()) {
                    var body = prepareRequestBodyForLog(requestXml);
                    gen.writeStringField("xml", body);
                }
                gen.writeStringField("soapService", descriptor.service);
                gen.writeEndObject();
            }))
            .log("SoapService requesting");
    }

    protected String prepareRequestBodyForLog(byte[] requestXml) {
        return new String(requestXml, StandardCharsets.UTF_8);
    }

    protected String prepareResponseBodyForLog(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }


    @Override
    public void observeHttpResponse(HttpClientResponse httpClientResponse) {
        this.httpCode = httpClientResponse.code();
        this.span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, httpClientResponse.code());
    }

    @Override
    public void observeResponseBody(byte[] resultXml) {
        requestLog
            .atInfo()
            .addKeyValue("soapRequest", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("soapMethod", descriptor.method);
                gen.writeStringField("soapService", descriptor.service);
                gen.writeStringField("soapStatus", "success");
                if (responseLog.isTraceEnabled()) {
                    var body = this.prepareResponseBodyForLog(resultXml);
                    gen.writeStringField("xml", body);
                }
                gen.writeEndObject();
            }))
            .log("SoapService received response");

    }


    @Override
    public void observeFailure(SoapResult.Failure result) {
        this.span.setStatus(StatusCode.ERROR);
        this.fault = result.fault();
        this.span.setAttribute(FAULT_CODE, result.fault().getFaultcode().toString());
        this.span.setAttribute(FAULT_ACTOR, result.fault().getFaultactor());
        this.responseLog
            .atInfo()
            .addKeyValue("soapResponse", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("soapMethod", descriptor.method);
                gen.writeStringField("soapService", descriptor.service);
                gen.writeStringField("soapStatus", "success");
                gen.writeStringField("soapFaultCode", result.fault().getFaultcode().toString());
                gen.writeStringField("soapFaultActor", result.fault().getFaultactor());
                gen.writeEndObject();
            }))
            .log("SoapService received 'failure'");
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
        this.responseLog
            .atInfo()
            .addKeyValue("soapResponse", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("soapMethod", descriptor.method);
                gen.writeStringField("soapService", descriptor.service);
                gen.writeStringField("soapStatus", "failure");
                gen.writeStringField("exceptionType", e.getClass().getCanonicalName());
                gen.writeEndObject();
            }))
            .log("SoapService received 'failure'");
    }

    @Override
    public void end() {
        var took = System.nanoTime() - this.start;
        if (this.error != null) {
            this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), this.error.getClass().getCanonicalName())
                .record(took, TimeUnit.NANOSECONDS);
        } else if (this.fault != null && this.fault.getDetail() != null && this.fault.getDetail().getAny() != null && !this.fault.getDetail().getAny().isEmpty()) {
            var faultDetail = this.fault.getDetail().getAny().getFirst();
            this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), faultDetail.getClass().getCanonicalName())
                .record(took, TimeUnit.NANOSECONDS);
        } else if (this.fault != null) {
            this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), "unknown error")
                .record(took, TimeUnit.NANOSECONDS);
        } else {
            this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), "")
                .record(took, TimeUnit.NANOSECONDS);
        }

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
    }
}

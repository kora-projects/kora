package ru.tinkoff.kora.opentelemetry.module.soap.client;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.SemanticAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTelemetry;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientTracer;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class OpentelemetrySoapClientTracer implements SoapClientTracer {

    private static final AttributeKey<String> FAULT_CODE = stringKey("fault.code");
    private static final AttributeKey<String> FAULT_ACTOR = stringKey("fault.actor");

    private final Tracer tracer;

    private final String serviceSimpleName;
    private final String soapMethod;
    private final String url;

    public OpentelemetrySoapClientTracer(Tracer tracer, String serviceSimpleName, String soapMethod, String url) {
        this.tracer = tracer;
        this.serviceSimpleName = serviceSimpleName;
        this.soapMethod = soapMethod;
        this.url = url;
    }

    @Override
    public SoapClientSpan createSpan(Context ctx, SoapEnvelope requestEnvelope) {
        var otctx = OpentelemetryContext.get(ctx);

        var builder = this.tracer.spanBuilder("SOAP " + serviceSimpleName + " " + soapMethod)
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext());

        builder.setAttribute(SemanticAttributes.RPC_SERVICE, serviceSimpleName);
        builder.setAttribute(SemanticAttributes.RPC_METHOD, soapMethod);
        builder.setAttribute(SemanticAttributes.RPC_SYSTEM, url);

        var span = builder.startSpan();

        var newCtx = otctx.add(span);
        OpentelemetryContext.set(ctx, newCtx);

        return new SoapClientSpan() {

            @Override
            public void success(SoapResult.Success result) {
                span.end();
                OpentelemetryContext.set(ctx, otctx);
            }

            @Override
            public void failure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure) {
                if (failure instanceof SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InvalidHttpCode ie) {
                    span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, ie.code());
                } else if (failure instanceof SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.InternalServerError se) {
                    span.setAttribute(FAULT_CODE.getKey(), se.result().fault().getFaultcode().toString());
                    span.setAttribute(FAULT_ACTOR.getKey(), se.result().fault().getFaultactor());
                } else if (failure instanceof SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure.ProcessException pe) {
                    span.setAttribute(SemanticAttributes.ERROR_TYPE.getKey(), pe.throwable().getClass().getCanonicalName());
                    span.recordException(pe.throwable());
                }
                span.setStatus(StatusCode.ERROR);
                span.end();
                OpentelemetryContext.set(ctx, otctx);
            }
        };
    }
}

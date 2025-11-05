package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.concurrent.TimeUnit;

public class DefaultGrpcServerObservation implements GrpcServerObservation {
    private static final Logger log = LoggerFactory.getLogger(DefaultGrpcServerObservation.class);
    private final long start = System.nanoTime();
    private final String service;
    private final String method;
    private final Metadata rqHeaders;
    private final Span span;
    private final Meter.MeterProvider<Timer> duration;
    private volatile Throwable error;
    private volatile Status status;

    public DefaultGrpcServerObservation(String service, String method, Metadata rqHeaders, Span span, Meter.MeterProvider<Timer> duration) {
        this.service = service;
        this.method = method;
        this.rqHeaders = rqHeaders;
        this.span = span;
        this.duration = duration;
    }

    @Override
    public void observeHeaders(Metadata headers) {
        W3CTraceContextPropagator.getInstance().inject(
            Context.root().with(this.span),
            headers,
            (carrier, key, value) -> carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        );
    }

    @Override
    public void observeRequest(int numMessages) {}

    @Override
    public void observeSendMessage(Object rs) {
        this.span.addEvent("rpc.message", Attributes.of(
            RpcIncubatingAttributes.RPC_MESSAGE_TYPE, RpcIncubatingAttributes.RpcMessageTypeIncubatingValues.SENT
        ));
    }

    @Override
    public void observeClose(Status status, Metadata trailers) {
        if (status.getCause() != null) {
            this.observeError(status.getCause());
        } else if (!status.isOk()) {
            this.span.setStatus(StatusCode.ERROR);
        }
        this.span.setAttribute(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE, status.getCode().value());
        this.status = status;
    }

    @Override
    public void observeCancel() {}

    @Override
    public void observeComplete() {}

    @Override
    public void observeHalfClosed() {}

    @Override
    public void observeReceiveMessage(Object rq) {
        this.span.addEvent("rpc.message", Attributes.of(
            RpcIncubatingAttributes.RPC_MESSAGE_TYPE, RpcIncubatingAttributes.RpcMessageTypeIncubatingValues.RECEIVED
        ));
    }

    @Override
    public void observeReady() {

    }

    @Override
    public void observeStart() {
        log.atInfo()
            .addKeyValue("grpcRequest", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringField("serviceName", this.service);
                gen.writeStringField("operation", this.service + "/" + this.method);
                if (log.isDebugEnabled()) {
                    gen.writeStringField("headers", this.rqHeaders.toString());
                }
                gen.writeEndObject();
            }))
            .log("GrpcCall received");
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var duration = System.nanoTime() - this.start;
        this.recordDuration(duration);
        this.closeSpan();
        this.logEnd(duration);
    }

    protected void logEnd(long duration) {
        var logArg = StructuredArgument.value(gen -> {
            gen.writeStartObject();
            gen.writeStringField("serviceName", this.service);
            gen.writeStringField("operation", this.service + "/" + this.method);
            gen.writeNumberField("processingTime", duration / 1_000_000);
            if (this.status != null) {
                gen.writeStringField("status", this.status.getCode().name());
            }
            if (this.error != null) {
                var exceptionType = this.error.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        });
        if (this.error == null) {
            log.atInfo()
                .addKeyValue("grpcError", logArg)
                .log("GrpcCall responded");
        } else {
            log.atWarn()
                .addKeyValue("grpcError", logArg)
                .setCause(this.error)
                .log("GrpcCall responded");
        }

    }

    protected void closeSpan() {
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }

    protected void recordDuration(long duration) {
        if (status != null) {
            this.duration.withTag(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(status.getCode().value())).record(duration, TimeUnit.NANOSECONDS);
        } else {
            this.duration.withTag(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), Integer.toString(Status.Code.UNKNOWN.value())).record(duration, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void observeError(Throwable e) {
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
        this.error = e;
    }
}

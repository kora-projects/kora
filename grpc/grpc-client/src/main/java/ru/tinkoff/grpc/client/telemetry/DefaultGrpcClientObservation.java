package ru.tinkoff.grpc.client.telemetry;

import io.grpc.Metadata;
import io.grpc.Status;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class DefaultGrpcClientObservation implements GrpcClientObservation {
    private final long start = System.nanoTime();
    private final Span span;
    private final Meter.MeterProvider<Timer> duration;
    @Nullable
    private Throwable error;
    private Status status;

    public DefaultGrpcClientObservation(Span span, Meter.MeterProvider<Timer> duration) {
        this.span = span;
        this.duration = duration;
    }

    @Override
    public void observeStart(Metadata headers) {
        W3CTraceContextPropagator.getInstance().inject(
            Context.root().with(this.span),
            headers,
            (carrier, key, value) -> carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        );
    }

    @Override
    public void observeSend(Object message) {
        this.span.addEvent("rpc.message", Attributes.of(
            RpcIncubatingAttributes.RPC_MESSAGE_TYPE, RpcIncubatingAttributes.RpcMessageTypeIncubatingValues.SENT
        ));
    }

    @Override
    public void observeReceive(Object message) {
        this.span.addEvent("rpc.message", Attributes.of(
            RpcIncubatingAttributes.RPC_MESSAGE_TYPE, RpcIncubatingAttributes.RpcMessageTypeIncubatingValues.RECEIVED
        ));
    }

    @Override
    public void observeClose(Status status, Metadata trailers) {
        this.status = status;
        if (!status.isOk()) {
            this.span.setStatus(StatusCode.ERROR);
            if (status.getCause() != null) {
                this.observeError(status.getCause());
            }
        }
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var grpcStatus = status != null
            ? Integer.toString(status.getCode().value())
            : "";
        var errorType = this.error != null
            ? this.error.getClass().getCanonicalName()
            : "";
        var took = System.nanoTime() - start;
        this.duration.withTags(Tags.of(
            Tag.of(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE.getKey(), grpcStatus),
            Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType)
        )).record(took, TimeUnit.NANOSECONDS);

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}

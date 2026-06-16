package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.koraframework.grpc.client.telemetry.GrpcClientObservation;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;

public class DefaultGrpcClientObservation implements GrpcClientObservation {

    protected final long started = System.nanoTime();
    protected final MethodDescriptor<?, ?> method;
    protected final DefaultGrpcClientTelemetry.TelemetryContext context;
    protected final Span span;
    protected final DefaultGrpcClientLoggerFactory.DefaultGrpcClientLogger logger;
    protected final DefaultGrpcClientMetricsFactory.DefaultGrpcClientMetrics metrics;
    @Nullable
    protected Throwable error;
    @Nullable
    protected Status status;

    public DefaultGrpcClientObservation(MethodDescriptor<?, ?> method,
                                        DefaultGrpcClientTelemetry.TelemetryContext context,
                                        Span span,
                                        DefaultGrpcClientLoggerFactory.DefaultGrpcClientLogger logger,
                                        DefaultGrpcClientMetricsFactory.DefaultGrpcClientMetrics metrics) {
        this.method = method;
        this.context = context;
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public void observeStart(Metadata headers) {
        W3CTraceContextPropagator.getInstance().inject(
            Context.root().with(this.span),
            headers,
            (carrier, key, value) -> carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        );
        this.logger.logRequest(method, headers);
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
        this.span.setAttribute(RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE, status.getCode().value());
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
        var processingTimeNanos = System.nanoTime() - started;
        this.metrics.record(method, status, error, processingTimeNanos);
        this.logger.logResponse(method, status, error, processingTimeNanos);

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        } else {
            var errorType = this.error.getClass().getCanonicalName();
            this.span.setStatus(StatusCode.ERROR, errorType);
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorType);
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

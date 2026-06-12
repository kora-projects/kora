package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import io.koraframework.grpc.server.telemetry.GrpcServerObservation;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

public class DefaultGrpcServerObservation implements GrpcServerObservation {

    protected final long started = System.nanoTime();
    protected final DefaultGrpcServerTelemetry.TelemetryContext context;
    protected final String service;
    protected final String method;
    protected final Metadata requestHeaders;
    protected final Span span;
    protected final DefaultGrpcServerLoggerFactory.DefaultGrpcServerLogger logger;
    protected final DefaultGrpcServerMetricsFactory.DefaultGrpcServerMetrics metrics;

    protected volatile Throwable error;
    protected volatile Status status;

    public DefaultGrpcServerObservation(DefaultGrpcServerTelemetry.TelemetryContext context,
                                        String service,
                                        String method,
                                        Metadata requestHeaders,
                                        Span span,
                                        DefaultGrpcServerLoggerFactory.DefaultGrpcServerLogger logger,
                                        DefaultGrpcServerMetricsFactory.DefaultGrpcServerMetrics metrics) {
        this.context = context;
        this.service = service;
        this.method = method;
        this.requestHeaders = requestHeaders;
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
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
    public void observeReady() {}

    @Override
    public void observeStart() {
        this.logger.logRequest(service, method, requestHeaders);
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var processingTimeNanos = System.nanoTime() - this.started;
        this.metrics.record(service, method, status, processingTimeNanos);
        this.closeSpan();
        this.logger.logResponse(service, method, status, error, processingTimeNanos);
    }

    protected void closeSpan() {
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
        this.error = e;
    }
}

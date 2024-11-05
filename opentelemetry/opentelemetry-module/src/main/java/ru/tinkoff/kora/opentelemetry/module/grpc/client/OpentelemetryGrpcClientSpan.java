package ru.tinkoff.kora.opentelemetry.module.grpc.client;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class OpentelemetryGrpcClientSpan implements GrpcClientTracer.GrpcClientSpan {
    private final Tracer tracer;
    private final Span span;
    private final ServiceDescriptor descriptor;
    private final URI uri;

    private static final AtomicIntegerFieldUpdater<OpentelemetryGrpcClientSpan> REQUESTS = AtomicIntegerFieldUpdater.newUpdater(OpentelemetryGrpcClientSpan.class, "requests");
    private volatile int requests = 0;
    private static final AtomicIntegerFieldUpdater<OpentelemetryGrpcClientSpan> RESPONSES = AtomicIntegerFieldUpdater.newUpdater(OpentelemetryGrpcClientSpan.class, "responses");
    private volatile int responses = 0;

    public OpentelemetryGrpcClientSpan(Tracer tracer, Span span, ServiceDescriptor descriptor, URI uri) {
        this.tracer = tracer;
        this.span = span;
        this.descriptor = descriptor;
        this.uri = uri;
    }

    @Override
    public void close(Exception e) {
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
        this.span.end();
    }

    @Override
    public void close(Status status, Metadata trailers) {
        this.span.end();
    }

    @Override
    public <RespT, ReqT> GrpcClientTracer.GrpcClientRequestSpan reqSpan(Context ctx, MethodDescriptor<ReqT, RespT> method, ReqT req) {
        var otctx = OpentelemetryContext.get(ctx);

        var span = this.tracer.spanBuilder("message")
            .setAttribute("rpc.method", Objects.requireNonNullElse(method.getBareMethodName(), "unknownMethod"))
            .setAttribute("rpc.service", this.descriptor.getName())
            .setAttribute("rpc.system", "grpc")
            .setAttribute("server.address", this.uri.getHost())
            .setAttribute("server.port", this.uri.getPort())
            .setAttribute("message.type", "SENT")
            .setAttribute("message.id", REQUESTS.incrementAndGet(this))
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext())
            .startSpan();
        var newCtx = otctx.add(span);
        OpentelemetryContext.set(ctx, newCtx);

        return new OpentelemetryGrpcClientRequestSpan(ctx, otctx, span);
    }

    @Override
    public <RespT, ReqT> GrpcClientTracer.GrpcClientResponseSpan resSpan(Context ctx, MethodDescriptor<ReqT, RespT> method, RespT message) {
        var otctx = OpentelemetryContext.get(ctx);

        var span = this.tracer.spanBuilder("message")
            .setAttribute("rpc.method", Objects.requireNonNullElse(method.getBareMethodName(), "unknownMethod"))
            .setAttribute("rpc.service", this.descriptor.getName())
            .setAttribute("rpc.system", "grpc")
            .setAttribute("server.address", this.uri.getHost())
            .setAttribute("server.port", this.uri.getPort())
            .setAttribute("message.type", "RECEIVED")
            .setAttribute("message.id", RESPONSES.incrementAndGet(this))
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext())
            .startSpan();
        var newCtx = otctx.add(span);
        OpentelemetryContext.set(ctx, newCtx);

        return new OpentelemetryGrpcClientResponseSpan(ctx, otctx, span);
    }

}

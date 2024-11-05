package ru.tinkoff.kora.opentelemetry.module.grpc.client;

import io.opentelemetry.api.trace.Span;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

public final class OpentelemetryGrpcClientRequestSpan implements GrpcClientTracer.GrpcClientRequestSpan {
    private final Context ctx;
    private final Span span;
    private final OpentelemetryContext parentCtx;

    public OpentelemetryGrpcClientRequestSpan(Context ctx, OpentelemetryContext parentCtx, Span span) {
        this.ctx = ctx;
        this.parentCtx = parentCtx;
        this.span = span;
    }

    @Override
    public void close(Exception e) {
        this.span.recordException(e).end();
        OpentelemetryContext.set(ctx, parentCtx);
    }

    @Override
    public void close() {
        this.span.end();
        OpentelemetryContext.set(ctx, parentCtx);
    }
}

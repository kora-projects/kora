package ru.tinkoff.kora.opentelemetry.module.grpc.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

public final class OpentelemetryGrpcClientResponseSpan implements GrpcClientTracer.GrpcClientResponseSpan {
    private final Context ctx;
    private final OpentelemetryContext parentCtx;
    private final Span span;

    public OpentelemetryGrpcClientResponseSpan(Context ctx, OpentelemetryContext parentCtx, Span span) {
        this.ctx = ctx;
        this.parentCtx = parentCtx;
        this.span = span;
    }

    @Override
    public void close(Exception e) {
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
        this.span.end();
        OpentelemetryContext.set(ctx, parentCtx);
    }

    @Override
    public void close() {
        this.span.setStatus(StatusCode.OK);
        this.span.end();
        OpentelemetryContext.set(ctx, parentCtx);
    }
}

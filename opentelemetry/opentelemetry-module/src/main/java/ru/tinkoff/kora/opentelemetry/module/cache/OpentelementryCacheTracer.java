package ru.tinkoff.kora.opentelemetry.module.cache;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.cache.telemetry.CacheTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class OpentelementryCacheTracer implements CacheTracer {

    private static final String TAG_OPERATION = "operation";
    private static final String TAG_CACHE_NAME = "cache";
    private static final String TAG_ORIGIN = "origin";

    private final Tracer tracer;

    public OpentelementryCacheTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    record OpentelemetryCacheSpan(Span span, Context currentContext, OpentelemetryContext context) implements CacheSpan {

        @Override
        public void recordSuccess() {
            span.setStatus(StatusCode.OK);
            span.end();
            OpentelemetryContext.set(currentContext, context);
        }

        @Override
        public void recordFailure(@Nullable Throwable throwable) {
            span.setStatus(StatusCode.ERROR);
            span.end();
            OpentelemetryContext.set(currentContext, context);
        }
    }

    @Override
    public CacheSpan trace(@Nonnull CacheTelemetry.Operation operation) {
        var context = Context.current();
        var traceContext = OpentelemetryContext.get(context);
        var span = this.tracer.spanBuilder("cache.call")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(traceContext.getContext())
            .setAttribute(TAG_OPERATION, operation.type().name())
            .setAttribute(TAG_CACHE_NAME, operation.cacheName())
            .setAttribute(TAG_ORIGIN, operation.origin())
            .startSpan();

        OpentelemetryContext.set(context, traceContext.add(span));
        return new OpentelemetryCacheSpan(span, context, traceContext);
    }
}

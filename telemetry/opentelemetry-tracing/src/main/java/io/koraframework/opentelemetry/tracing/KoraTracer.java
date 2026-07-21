package io.koraframework.opentelemetry.tracing;

import io.koraframework.common.telemetry.OpentelemetryContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("overloads")
public class KoraTracer {

    private static final Consumer<SpanBuilder> NOOP = _ -> {};

    private final Tracer tracer;

    public KoraTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public Tracer tracer() {
        return this.tracer;
    }

    public <T, E extends Throwable> T traceParent(String spanName, TraceCallable<T, E> callable) throws E {
        return this.traceParent(spanName, NOOP, callable);
    }

    public <E extends Throwable> void traceParent(String spanName, TraceRunnable<E> runnable) throws E {
        this.traceParent(spanName, NOOP, runnable);
    }

    private <T, E extends Throwable> T traceParent(String spanName, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.trace(spanName, builder -> builder.setParent(Context.current()), Context::current, configure, callable);
    }

    private <E extends Throwable> void traceParent(String spanName, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceParent(spanName, configure, span -> {
            runnable.run(span);
            return null;
        });
    }

    public <T, E extends Throwable> T traceNew(String spanName, TraceCallable<T, E> callable) throws E {
        return this.traceNew(spanName, NOOP, callable);
    }

    public <E extends Throwable> void traceNew(String spanName, TraceRunnable<E> runnable) throws E {
        this.traceNew(spanName, NOOP, runnable);
    }

    private <T, E extends Throwable> T traceNew(String spanName, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.trace(spanName, SpanBuilder::setNoParent, Context::root, configure, callable);
    }

    private <E extends Throwable> void traceNew(String spanName, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceNew(spanName, configure, span -> {
            runnable.run(span);
            return null;
        });
    }

    private <T, E extends Throwable> T trace(String spanName,
                                             Consumer<SpanBuilder> defaults,
                                             Supplier<Context> contextFactory,
                                             Consumer<SpanBuilder> configure,
                                             TraceCallable<T, E> callable) throws E {
        var span = this.buildSpan(spanName, defaults, configure);
        return ScopedValue.where(OpentelemetryContext.VALUE, contextFactory.get().with(span))
            .call(() -> {
                try {
                    var result = callable.call(span);
                    span.setStatus(StatusCode.OK);
                    return result;
                } catch (Throwable e) {
                    this.recordError(span, e);
                    throw e;
                } finally {
                    span.end();
                }
            });
    }

    private Span buildSpan(String spanName, Consumer<SpanBuilder> defaults, Consumer<SpanBuilder> configure) {
        var builder = this.tracer.spanBuilder(spanName);
        defaults.accept(builder);
        configure.accept(builder);
        return builder.startSpan();
    }

    private void recordError(Span span, Throwable error) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, error.getMessage());
    }

    @FunctionalInterface
    public interface TraceCallable<T, E extends Throwable> {
        T call(Span span) throws E;
    }

    @FunctionalInterface
    public interface TraceRunnable<E extends Throwable> {
        void run(Span span) throws E;
    }
}

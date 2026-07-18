package io.koraframework.opentelemetry.tracing;

import io.koraframework.common.telemetry.OpentelemetryContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("overloads")
public class KoraTracer {

    private static final Consumer<SpanBuilder> NOOP = builder -> {};

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

    public <T, E extends Throwable> T traceParent(String spanName, Map<String, String> attributes, TraceCallable<T, E> callable) throws E {
        return this.traceParent(spanName, attributes, NOOP, callable);
    }

    public <T, E extends Throwable> T traceParent(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.traceParent(spanName, combine(attributes, configure), callable);
    }

    public <T, E extends Throwable> T traceParent(String spanName, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.trace(spanName, builder -> builder.setParent(Context.current()), Context::current, configure, callable);
    }

    public <E extends Throwable> void traceParent(String spanName, TraceRunnable<E> runnable) throws E {
        this.traceParent(spanName, NOOP, runnable);
    }

    public <E extends Throwable> void traceParent(String spanName, Map<String, String> attributes, TraceRunnable<E> runnable) throws E {
        this.traceParent(spanName, attributes, NOOP, runnable);
    }

    public <E extends Throwable> void traceParent(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceParent(spanName, combine(attributes, configure), runnable);
    }

    public <E extends Throwable> void traceParent(String spanName, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceParent(spanName, configure, span -> {
            runnable.run(span);
            return null;
        });
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentAsync(String spanName, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceParentAsync(spanName, NOOP, callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentAsync(String spanName, Map<String, String> attributes, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceParentAsync(spanName, attributes, NOOP, callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentAsync(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceParentAsync(spanName, combine(attributes, configure), callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentAsync(String spanName, Consumer<SpanBuilder> configure, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceAsync(spanName, builder -> builder.setParent(Context.current()), Context::current, configure, callable);
    }

    public <T, E extends Throwable> T traceRoot(String spanName, TraceCallable<T, E> callable) throws E {
        return this.traceRoot(spanName, NOOP, callable);
    }

    public <T, E extends Throwable> T traceRoot(String spanName, Map<String, String> attributes, TraceCallable<T, E> callable) throws E {
        return this.traceRoot(spanName, attributes, NOOP, callable);
    }

    public <T, E extends Throwable> T traceRoot(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.traceRoot(spanName, combine(attributes, configure), callable);
    }

    public <T, E extends Throwable> T traceRoot(String spanName, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.trace(spanName, SpanBuilder::setNoParent, Context::root, configure, callable);
    }

    public <E extends Throwable> void traceRoot(String spanName, TraceRunnable<E> runnable) throws E {
        this.traceRoot(spanName, NOOP, runnable);
    }

    public <E extends Throwable> void traceRoot(String spanName, Map<String, String> attributes, TraceRunnable<E> runnable) throws E {
        this.traceRoot(spanName, attributes, NOOP, runnable);
    }

    public <E extends Throwable> void traceRoot(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceRoot(spanName, combine(attributes, configure), runnable);
    }

    public <E extends Throwable> void traceRoot(String spanName, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceRoot(spanName, configure, span -> {
            runnable.run(span);
            return null;
        });
    }

    public <T, E extends Throwable> CompletionStage<T> traceRootAsync(String spanName, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceRootAsync(spanName, NOOP, callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceRootAsync(String spanName, Map<String, String> attributes, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceRootAsync(spanName, attributes, NOOP, callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceRootAsync(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceRootAsync(spanName, combine(attributes, configure), callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceRootAsync(String spanName, Consumer<SpanBuilder> configure, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceAsync(spanName, SpanBuilder::setNoParent, Context::root, configure, callable);
    }

    public <T, E extends Throwable> T traceParentLink(String spanName, TraceCallable<T, E> callable) throws E {
        return this.traceParentLink(spanName, NOOP, callable);
    }

    public <T, E extends Throwable> T traceParentLink(String spanName, Map<String, String> attributes, TraceCallable<T, E> callable) throws E {
        return this.traceParentLink(spanName, attributes, NOOP, callable);
    }

    public <T, E extends Throwable> T traceParentLink(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.traceParentLink(spanName, combine(attributes, configure), callable);
    }

    public <T, E extends Throwable> T traceParentLink(String spanName, Consumer<SpanBuilder> configure, TraceCallable<T, E> callable) throws E {
        return this.trace(spanName, KoraTracer::setParentLink, Context::root, configure, callable);
    }

    public <E extends Throwable> void traceParentLink(String spanName, TraceRunnable<E> runnable) throws E {
        this.traceParentLink(spanName, NOOP, runnable);
    }

    public <E extends Throwable> void traceParentLink(String spanName, Map<String, String> attributes, TraceRunnable<E> runnable) throws E {
        this.traceParentLink(spanName, attributes, NOOP, runnable);
    }

    public <E extends Throwable> void traceParentLink(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceParentLink(spanName, combine(attributes, configure), runnable);
    }

    public <E extends Throwable> void traceParentLink(String spanName, Consumer<SpanBuilder> configure, TraceRunnable<E> runnable) throws E {
        this.traceParentLink(spanName, configure, span -> {
            runnable.run(span);
            return null;
        });
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentLinkAsync(String spanName, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceParentLinkAsync(spanName, NOOP, callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentLinkAsync(String spanName, Map<String, String> attributes, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceParentLinkAsync(spanName, attributes, NOOP, callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentLinkAsync(String spanName, Map<String, String> attributes, Consumer<SpanBuilder> configure, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceParentLinkAsync(spanName, combine(attributes, configure), callable);
    }

    public <T, E extends Throwable> CompletionStage<T> traceParentLinkAsync(String spanName, Consumer<SpanBuilder> configure, TraceAsyncCallable<T, E> callable) throws E {
        return this.traceAsync(spanName, KoraTracer::setParentLink, Context::root, configure, callable);
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
                } catch (Exception e) {
                    this.recordError(span, e);
                    throw e;
                } catch (Error e) {
                    this.recordError(span, e);
                    throw e;
                } finally {
                    span.end();
                }
            });
    }

    private <T, E extends Throwable> CompletionStage<T> traceAsync(String spanName,
                                                                      Consumer<SpanBuilder> defaults,
                                                                      Supplier<Context> contextFactory,
                                                                      Consumer<SpanBuilder> configure,
                                                                      TraceAsyncCallable<T, E> callable) throws E {
        var span = this.buildSpan(spanName, defaults, configure);
        try {
            return Objects.requireNonNull(ScopedValue.where(OpentelemetryContext.VALUE, contextFactory.get().with(span))
                    .call(() -> callable.call(span)))
                .whenComplete((result, error) -> {
                    if (error == null) {
                        span.setStatus(StatusCode.OK);
                    } else {
                        this.recordError(span, unwrap(error));
                    }
                    span.end();
                });
        } catch (Exception e) {
            this.recordError(span, e);
            span.end();
            throw e;
        } catch (Error e) {
            this.recordError(span, e);
            span.end();
            throw e;
        }
    }

    private Span buildSpan(String spanName, Consumer<SpanBuilder> defaults, Consumer<SpanBuilder> configure) {
        var builder = this.tracer.spanBuilder(spanName);
        defaults.accept(builder);
        configure.accept(builder);
        return builder.startSpan();
    }

    private static Consumer<SpanBuilder> combine(Map<String, String> attributes, Consumer<SpanBuilder> configure) {
        Objects.requireNonNull(attributes);
        Objects.requireNonNull(configure);
        return builder -> {
            attributes.forEach(builder::setAttribute);
            configure.accept(builder);
        };
    }

    private static void setParentLink(SpanBuilder builder) {
        builder.setNoParent();
        var spanContext = Span.fromContext(Context.current()).getSpanContext();
        if (spanContext.isValid()) {
            builder.addLink(spanContext);
        }
    }

    private void recordError(Span span, Throwable error) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, error.getMessage());
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return error;
    }

    @FunctionalInterface
    public interface TraceCallable<T, E extends Throwable> {
        T call(Span span) throws E;
    }

    @FunctionalInterface
    public interface TraceRunnable<E extends Throwable> {
        void run(Span span) throws E;
    }

    @FunctionalInterface
    public interface TraceAsyncCallable<T, E extends Throwable> {
        CompletionStage<T> call(Span span) throws E;
    }
}

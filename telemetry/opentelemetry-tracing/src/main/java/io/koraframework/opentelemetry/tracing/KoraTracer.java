package io.koraframework.opentelemetry.tracing;

import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;

import java.util.function.*;

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

        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = baseContext.with(
            OpentelemetryContextStorage.OPENTELEMETRY_KEY,
            contextFactory.get().with(span)
        );

        try {
            return KoraContext.runWith(updatedContext, new TraceCallableRunner<>(this, span, callable));
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                @SuppressWarnings("unchecked")
                E throwable = (E) cause;
                throw throwable;
            }
            throw e;
        }
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

    private static final class TraceCallableRunner<T, E extends Throwable> implements Supplier<T> {
        private final KoraTracer tracer;
        private final Span span;
        private final TraceCallable<T, E> callable;

        public TraceCallableRunner(KoraTracer tracer, Span span, TraceCallable<T, E> callable) {
            this.tracer = tracer;
            this.span = span;
            this.callable = callable;
        }

        @Override
        public T get() {
            try {
                var result = callable.call(span);
                span.setStatus(StatusCode.OK);
                return result;
            } catch (Throwable e) {
                tracer.recordError(span, e);
                throw new RuntimeException(e);
            } finally {
                span.end();
            }
        }
    }
}

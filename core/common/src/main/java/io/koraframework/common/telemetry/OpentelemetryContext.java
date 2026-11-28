package io.koraframework.common.telemetry;

import io.koraframework.common.context.*;
import io.opentelemetry.context.*;

import java.util.concurrent.*;
import java.util.function.*;

public class OpentelemetryContext implements Context {

    private final Context delegate;

    public OpentelemetryContext(Context delegate) {
        this.delegate = delegate;
    }

    @Override
    public Scope makeCurrent() {
        throw new IllegalStateException("makeCurrent() is not supported in ScopedValue environments");
    }

    @Override
    public <V> V get(ContextKey<V> key) {
        return delegate.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
        return new OpentelemetryContext(delegate.with(k1, v1));
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        return new WrappedRunnable(this, runnable);
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> callable) {
        return new WrappedCallable<>(this, callable);
    }

    @Override
    public <T, U> Function<T, U> wrapFunction(Function<T, U> function) {
        return new WrappedFunction<>(this, function);
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrapFunction(BiFunction<T, U, V> function) {
        return new WrappedBiFunction<>(this, function);
    }

    @Override
    public <T> Consumer<T> wrapConsumer(Consumer<T> consumer) {
        return new WrappedConsumer<>(this, consumer);
    }

    @Override
    public <T, U> BiConsumer<T, U> wrapConsumer(BiConsumer<T, U> consumer) {
        return new WrappedBiConsumer<>(this, consumer);
    }

    @Override
    public <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        return new WrappedSupplier<>(this, supplier);
    }

    private static KoraContext createUpdatedContext(OpentelemetryContext ctx) {
        KoraContext current = KoraContext.current();
        if (current == null) {
            return KoraContext.empty().with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, ctx);
        }
        return current.with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, ctx);
    }

    private static final class WrappedRunnable implements Runnable {
        private final OpentelemetryContext ctx;
        private final Runnable delegate;

        WrappedRunnable(OpentelemetryContext ctx, Runnable delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public void run() {
            KoraContext.runWith(createUpdatedContext(ctx), new RunnableRunner(delegate));
        }
    }

    private static final class RunnableRunner implements Supplier<Void> {
        private final Runnable runnable;

        RunnableRunner(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public Void get() {
            runnable.run();
            return null;
        }
    }

    private static final class WrappedCallable<T> implements Callable<T> {
        private final OpentelemetryContext ctx;
        private final Callable<T> delegate;

        WrappedCallable(OpentelemetryContext ctx, Callable<T> delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public T call() throws Exception {
            try {
                return KoraContext.runWith(createUpdatedContext(ctx), new CallableRunner<>(delegate));
            } catch (RuntimeException e) {
                if (e.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        }
    }

    private static final class CallableRunner<T> implements Supplier<T> {
        private final Callable<T> callable;

        CallableRunner(Callable<T> callable) {
            this.callable = callable;
        }

        @Override
        public T get() {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class WrappedFunction<T, U> implements Function<T, U> {
        private final OpentelemetryContext ctx;
        private final Function<T, U> delegate;

        WrappedFunction(OpentelemetryContext ctx, Function<T, U> delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public U apply(T t) {
            return KoraContext.runWith(createUpdatedContext(ctx), new FunctionRunner<>(delegate, t));
        }
    }

    private static final class FunctionRunner<T, U> implements Supplier<U> {
        private final Function<T, U> function;
        private final T arg;

        FunctionRunner(Function<T, U> function, T arg) {
            this.function = function;
            this.arg = arg;
        }

        @Override
        public U get() {
            return function.apply(arg);
        }
    }

    private static final class WrappedBiFunction<T, U, V> implements BiFunction<T, U, V> {
        private final OpentelemetryContext ctx;
        private final BiFunction<T, U, V> delegate;

        WrappedBiFunction(OpentelemetryContext ctx, BiFunction<T, U, V> delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public V apply(T t, U u) {
            return KoraContext.runWith(createUpdatedContext(ctx), new BiFunctionRunner<>(delegate, t, u));
        }
    }

    private static final class BiFunctionRunner<T, U, V> implements Supplier<V> {
        private final BiFunction<T, U, V> function;
        private final T arg1;
        private final U arg2;

        BiFunctionRunner(BiFunction<T, U, V> function, T arg1, U arg2) {
            this.function = function;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public V get() {
            return function.apply(arg1, arg2);
        }
    }

    private static final class WrappedConsumer<T> implements Consumer<T> {
        private final OpentelemetryContext ctx;
        private final Consumer<T> delegate;

        WrappedConsumer(OpentelemetryContext ctx, Consumer<T> delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public void accept(T t) {
            KoraContext.runWith(createUpdatedContext(ctx), new ConsumerRunner<>(delegate, t));
        }
    }

    private static final class ConsumerRunner<T> implements Supplier<Void> {
        private final Consumer<T> consumer;
        private final T arg;

        ConsumerRunner(Consumer<T> consumer, T arg) {
            this.consumer = consumer;
            this.arg = arg;
        }

        @Override
        public Void get() {
            consumer.accept(arg);
            return null;
        }
    }

    private static final class WrappedBiConsumer<T, U> implements BiConsumer<T, U> {
        private final OpentelemetryContext ctx;
        private final BiConsumer<T, U> delegate;

        WrappedBiConsumer(OpentelemetryContext ctx, BiConsumer<T, U> delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public void accept(T t, U u) {
            KoraContext.runWith(createUpdatedContext(ctx), new BiConsumerRunner<>(delegate, t, u));
        }
    }

    private static final class BiConsumerRunner<T, U> implements Supplier<Void> {
        private final BiConsumer<T, U> consumer;
        private final T arg1;
        private final U arg2;

        BiConsumerRunner(BiConsumer<T, U> consumer, T arg1, U arg2) {
            this.consumer = consumer;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public Void get() {
            consumer.accept(arg1, arg2);
            return null;
        }
    }

    private static final class WrappedSupplier<T> implements Supplier<T> {
        private final OpentelemetryContext ctx;
        private final Supplier<T> delegate;

        WrappedSupplier(OpentelemetryContext ctx, Supplier<T> delegate) {
            this.ctx = ctx;
            this.delegate = delegate;
        }

        @Override
        public T get() {
            return KoraContext.runWith(createUpdatedContext(ctx), new WrappedSupplierRunner<>(delegate));
        }
    }

    private static final class WrappedSupplierRunner<T> implements Supplier<T> {
        private final Supplier<T> supplier;

        WrappedSupplierRunner(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            return supplier.get();
        }
    }
}

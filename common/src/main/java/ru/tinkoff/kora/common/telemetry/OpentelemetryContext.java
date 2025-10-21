package ru.tinkoff.kora.common.telemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

import java.util.concurrent.Callable;
import java.util.function.*;

public class OpentelemetryContext implements Context {
    public static final ScopedValue<Context> VALUE = ScopedValue.newInstance();

    private final Context delegate;

    public OpentelemetryContext(Context delegate) {
        this.delegate = delegate;
    }

    @Override
    public Scope makeCurrent() {
        throw new IllegalStateException();
    }

    @Override
    public <V> V get(ContextKey<V> key) {
        return delegate.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> k1, V v1) {
        return delegate.with(k1, v1);
    }

    @Override
    public Runnable wrap(Runnable runnable) {
        return () -> ScopedValue.where(VALUE, this).run(runnable);

    }

    @Override
    public <T> Callable<T> wrap(Callable<T> callable) {
        return () -> ScopedValue.where(VALUE, this).call(callable::call);
    }


    @Override
    public <T, U> Function<T, U> wrapFunction(Function<T, U> function) {
        return t -> ScopedValue.where(VALUE, this)
            .call(() -> function.apply(t));
    }

    @Override
    public <T, U, V> BiFunction<T, U, V> wrapFunction(BiFunction<T, U, V> function) {
        return (t, u) -> ScopedValue.where(VALUE, this)
            .call(() -> function.apply(t, u));
    }

    @Override
    public <T> Consumer<T> wrapConsumer(Consumer<T> consumer) {
        return (t) -> ScopedValue.where(VALUE, this)
            .run(() -> consumer.accept(t));
    }

    @Override
    public <T, U> BiConsumer<T, U> wrapConsumer(BiConsumer<T, U> consumer) {
        return (t, u) -> ScopedValue.where(VALUE, this)
            .run(() -> consumer.accept(t, u));
    }

    @Override
    public <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        return () -> ScopedValue.where(VALUE, this)
            .call(supplier::get);
    }
}

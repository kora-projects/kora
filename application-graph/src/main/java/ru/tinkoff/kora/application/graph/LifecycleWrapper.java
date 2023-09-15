package ru.tinkoff.kora.application.graph;

public class LifecycleWrapper<T> implements Lifecycle, Wrapped<T> {
    private final T value;
    private final ThrowingConsumer<T> init;
    private final ThrowingConsumer<T> release;

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    public LifecycleWrapper(T value, ThrowingConsumer<T> init, ThrowingConsumer<T> release) {
        this.value = value;
        this.init = init;
        this.release = release;
    }

    @Override
    public void init() throws Exception {
        this.init.accept(this.value);
    }

    @Override
    public void release() throws Exception {
        this.release.accept(this.value);
    }

    @Override
    public T value() {
        return this.value;
    }
}

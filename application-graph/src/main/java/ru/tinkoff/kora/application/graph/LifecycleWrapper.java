package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

import java.util.function.Function;

public class LifecycleWrapper<T> implements Lifecycle, Wrapped<T> {
    private final T value;
    private final Function<T, Mono<Void>> init;
    private final Function<T, Mono<Void>> release;

    public LifecycleWrapper(T value, Function<T, Mono<Void>> init, Function<T, Mono<Void>> release) {
        this.value = value;
        this.init = init;
        this.release = release;
    }

    @Override
    public Mono<Void> init() {
        return this.init.apply(this.value);
    }

    @Override
    public Mono<Void> release() {
        return this.release.apply(this.value);
    }

    @Override
    public T value() {
        return this.value;
    }
}

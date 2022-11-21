package ru.tinkoff.kora.application.graph;

import java.util.Optional;
import java.util.function.Function;

public interface PromiseOf<T> {

    Optional<T> get();

    default <Q> PromiseOf<Q> map(Function<T, Q> mapper) {
        return () -> {
            var value = PromiseOf.this.get();
            return value.map(mapper);
        };
    }

    default PromiseOf<Optional<T>> optional() {
        return map(Optional::ofNullable);
    }

    static <T> PromiseOf<T> of(T value) {
        return () -> Optional.ofNullable(value);
    }

    static <T> PromiseOf<T> promiseOfNull() {
        return Optional::empty;
    }

    static <T> PromiseOf<Optional<T>> emptyOptional() {
        return Optional::empty;
    }
}

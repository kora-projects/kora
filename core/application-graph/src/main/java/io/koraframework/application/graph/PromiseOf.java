package io.koraframework.application.graph;

import java.util.Optional;
import java.util.function.Function;

public interface PromiseOf<T> {

    Optional<T> get();

    default <Q> PromiseOf<Q> map(Function<T, Q> mapper) {
        return new MappedPromise<>(this, mapper);
    }

    default PromiseOf<Optional<T>> optional() {
        return new MappedPromise<>(this, new OptionalMapper<>());
    }

    static <T> PromiseOf<T> of(T value) {
        return new ValuePromise<>(value);
    }

    static <T> PromiseOf<T> promiseOfNull() {
        return new EmptyPromise<>();
    }

    static <T> PromiseOf<Optional<T>> emptyOptional() {
        return new EmptyPromise<>();
    }

    final class MappedPromise<T, Q> implements PromiseOf<Q> {
        private final PromiseOf<T> source;
        private final Function<T, Q> mapper;

        public MappedPromise(PromiseOf<T> source, Function<T, Q> mapper) {
            this.source = source;
            this.mapper = mapper;
        }

        @Override
        public Optional<Q> get() {
            var value = source.get();
            return value.map(mapper);
        }
    }

    final class OptionalMapper<T> implements Function<T, Optional<T>> {
        @Override
        public Optional<T> apply(T t) {
            return Optional.ofNullable(t);
        }
    }

    final class ValuePromise<T> implements PromiseOf<T> {
        private final T value;

        public ValuePromise(T value) {
            this.value = value;
        }

        @Override
        public Optional<T> get() {
            return Optional.ofNullable(value);
        }
    }

    final class EmptyPromise<T> implements PromiseOf<T> {
        @Override
        public Optional<T> get() {
            return Optional.empty();
        }
    }
}

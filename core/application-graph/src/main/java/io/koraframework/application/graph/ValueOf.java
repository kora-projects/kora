package io.koraframework.application.graph;

import java.util.Optional;
import java.util.function.Function;

public interface ValueOf<T> {

    T get();

    default <Q> ValueOf<Q> map(Function<T, Q> mapper) {
        return new MappedValueOf<>(this, mapper);
    }

    default ValueOf<Optional<T>> optional() {
        return new OptionalValueOf<>(this);
    }

    static <T> ValueOf<Optional<T>> emptyOptional() {
        return new EmptyOptionalValueOf<>();
    }

    final class MappedValueOf<T, Q> implements ValueOf<Q> {
        private final ValueOf<T> source;
        private final Function<T, Q> mapper;

        public MappedValueOf(ValueOf<T> source, Function<T, Q> mapper) {
            this.source = source;
            this.mapper = mapper;
        }

        @Override
        public Q get() {
            return mapper.apply(source.get());
        }
    }

    final class OptionalValueOf<T> implements ValueOf<Optional<T>> {
        private final ValueOf<T> source;

        public OptionalValueOf(ValueOf<T> source) {
            this.source = source;
        }

        @Override
        public Optional<T> get() {
            return Optional.of(source.get());
        }
    }

    final class EmptyOptionalValueOf<T> implements ValueOf<Optional<T>> {
        @Override
        public Optional<T> get() {
            return Optional.empty();
        }
    }
}

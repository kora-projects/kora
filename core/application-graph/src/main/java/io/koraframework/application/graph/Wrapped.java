package io.koraframework.application.graph;

import java.util.function.Function;

public interface Wrapped<T> {

    T value();

    static <T> ValueOf<T> unwrap(ValueOf<Wrapped<T>> valueOf) {
        return new ValueOf.MappedValueOf<>(valueOf, new UnwrapMapper<>());
    }

    final class UnwrapMapper<T> implements Function<Wrapped<T>, T> {
        @Override
        public T apply(Wrapped<T> wrapped) {
            return wrapped.value();
        }
    }
}

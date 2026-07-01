package io.koraframework.application.graph;

import java.util.Optional;
import java.util.function.Function;

public interface ValueOf<T> {
    T get();

    default <Q> ValueOf<Q> map(Function<T, Q> mapper) {
        return () -> {
            var value = ValueOf.this.get();
            return mapper.apply(value);
        };
    }

    default ValueOf<Optional<T>> optional() {
        return () -> Optional.of(ValueOf.this.get());
    }

    static <T> ValueOf<Optional<T>> emptyOptional() {
        return Optional::empty;
    }
}

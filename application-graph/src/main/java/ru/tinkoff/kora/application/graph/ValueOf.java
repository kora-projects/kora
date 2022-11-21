package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

public interface ValueOf<T> {
    T get();

    Mono<Void> refresh();

    default <Q> ValueOf<Q> map(Function<T, Q> mapper) {
        return new ValueOf<>() {
            @Override
            public Q get() {
                var value = ValueOf.this.get();
                return mapper.apply(value);
            }

            @Override
            public Mono<Void> refresh() {
                return ValueOf.this.refresh();
            }
        };
    }

    default ValueOf<Optional<T>> optional() {
        return new ValueOf<>() {
            @Override
            public Optional<T> get() {
                return Optional.of(ValueOf.this.get());
            }

            @Override
            public Mono<Void> refresh() {
                return ValueOf.this.refresh();
            }
        };
    }

    static <T> ValueOf<Optional<T>> emptyOptional() {
        return new ValueOf<>() {
            @Override
            public Optional<T> get() {
                return Optional.empty();
            }

            @Override
            public Mono<Void> refresh() {
                return Mono.empty();
            }
        };
    }

    static <T> ValueOf<T> valueOfNull() {
        return new ValueOf<>() {
            @Override
            public T get() {
                return null;
            }

            @Override
            public Mono<Void> refresh() {
                return Mono.empty();
            }
        };
    }
}

package io.koraframework.common;

import org.jspecify.annotations.Nullable;

public sealed interface Either<A, B> {

    @Nullable
    A left();

    @Nullable
    B right();

    default boolean isLeft() {
        return this instanceof Either.Left<A, B>;
    }

    default boolean isRight() {
        return this instanceof Either.Right<A, B>;
    }

    record Left<A, B>(A value) implements Either<A, B> {
        @Override
        public A left() {
            return value;
        }

        @Nullable
        @Override
        public B right() {
            return null;
        }
    }

    record Right<A, B>(B value) implements Either<A, B> {

        @Nullable
        @Override
        public A left() {
            return null;
        }

        @Override
        public B right() {
            return value;
        }
    }

    static <A, B> Either<A, B> left(A value) {
        return new Left<>(value);
    }

    static <A, B> Either<A, B> right(B value) {
        return new Right<>(value);
    }
}

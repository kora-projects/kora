package ru.tinkoff.kora.common.util;

import javax.annotation.Nullable;

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

        @Override
        public B right() {
            return null;
        }
    }

    record Right<A, B>(B value) implements Either<A, B> {

        @Override
        @Nullable
        public A left() {
            return null;
        }

        @Override
        @Nullable
        public B right() {
            return value;
        }
    }

    static <A, B> Either<A, B> left(A value) {
        return new Left<A, B>(value);
    }

    static <A, B> Either<A, B> right(B value) {
        return new Right<A, B>(value);
    }
}

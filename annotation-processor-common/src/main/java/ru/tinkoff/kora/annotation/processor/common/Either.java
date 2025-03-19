package ru.tinkoff.kora.annotation.processor.common;

public record Either<L, R>(boolean isLeft, L left, R right) {
    public static <L, R> Either<L, R> left(L left) {
        return new Either<>(true, left, null);
    }

    public static <L, R> Either<L, R> right(R right) {
        return new Either<>(true, null, right);
    }

    public boolean isRight() {
        return !isLeft;
    }
}


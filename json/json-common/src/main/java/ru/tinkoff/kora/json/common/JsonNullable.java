package ru.tinkoff.kora.json.common;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

public interface JsonNullable<T> {

    boolean isDefined();

    default boolean isNullable() {
        return value() == null;
    }

    @Nullable
    T value();

    static <T> JsonNullable<T> undefined() {
        return JsonNullableContainer.UNDEFINED;
    }

    static <T> JsonNullable<T> nullable() {
        return JsonNullableContainer.NULLABLE;
    }

    static <T> JsonNullable<T> of(@Nonnull T value) {
        Objects.requireNonNull(value, "JsonNullable#of require non-null, but got null");
        return new JsonNullableContainer<>(true, false, value);
    }

    static <T> JsonNullable<T> ofNullable(@Nullable T value) {
        return new JsonNullableContainer<>(true, value == null, value);
    }
}

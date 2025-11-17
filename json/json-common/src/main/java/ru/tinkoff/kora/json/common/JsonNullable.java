package ru.tinkoff.kora.json.common;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

@SuppressWarnings("unchecked")
public record JsonNullable<T>(@Nullable T value) implements JsonValue<T> {

    static final JsonNullable NULL = new JsonNullable(null);

    public static <T> JsonNullable<T> of(@Nonnull T value) {
        Objects.requireNonNull(value, "JsonNullable#of require non-null, but got null");
        return new JsonNullable<>(value);
    }

    public static <T> JsonNullable<T> ofNullable(@Nullable T value) {
        return (value == null)
            ? JsonNullable.NULL
            : new JsonNullable<>(value);
    }

    public static <T> JsonNullable<T> nullValue() {
        return JsonNullable.NULL;
    }

    @Override
    public boolean isDefined() {
        return true;
    }

    @Override
    public boolean isNull() {
        return value == null;
    }

    @Override
    public boolean haveValue() {
        return value != null;
    }

    @Nullable
    @Override
    public T value() {
        return value;
    }
}

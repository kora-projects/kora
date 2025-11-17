package ru.tinkoff.kora.json.common;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

@SuppressWarnings("unchecked")
public record JsonUndefined<T>(@Nullable T value) implements JsonValue<T> {

    static final JsonUndefined UNDEFINED = new JsonUndefined(null);

    public static <T> JsonUndefined<T> of(@Nonnull T value) {
        Objects.requireNonNull(value, "JsonUndefined#of require non-null, but got null");
        return new JsonUndefined<>(value);
    }

    public static <T> JsonUndefined<T> ofUndefined(@Nullable T value) {
        return (value == null)
            ? JsonUndefined.UNDEFINED
            : new JsonUndefined<>(value);
    }

    public static <T> JsonUndefined<T> undefined() {
        return JsonUndefined.UNDEFINED;
    }

    @Override
    public boolean isDefined() {
        return value != null;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean haveValue() {
        return value != null;
    }

    @Override
    public T value() {
        if (value == null) {
            throw new NullPointerException("JsonUndefined is undefined");
        } else {
            return value;
        }
    }
}

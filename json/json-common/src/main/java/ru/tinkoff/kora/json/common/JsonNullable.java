package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * <b>Русский</b>: Специальный тип который позволяет указывать как записывать/считывать поля из JSON в состояниях, когда
 * поле указано и/или является `null` или не является `null` в JSON
 * Работает как для {@link ru.tinkoff.kora.json.common.annotation.JsonWriter} так и {@link ru.tinkoff.kora.json.common.annotation.JsonReader}
 * <hr>
 * <b>English</b>: A special type that allows you to specify how to write/read fields from JSON in states
 * where the field is specified and/or is `null` or is not `null` in JSON
 * Works for both {@link ru.tinkoff.kora.json.common.annotation.JsonWriter} and {@link ru.tinkoff.kora.json.common.annotation.JsonReader}
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Json
 * record Example(JsonNullable<String> definedNonNull,
 *                JsonNullable<String> definedNull,
 *                JsonNullable<String> undefined) { }
 * }
 * </pre>
 * →
 * <pre>
 * {@code
 * jsonWriter.toByteArray(
 *      new Example(JsonNullable.of("Interstellar"),
 *                  JsonNullable.nullValue(),
 *                  JsonNullable.undefined())
 *  );
 * }
 * </pre>
 * JsonNullable.undefined() is fully omitted cause it is undefined  →
 * <pre>{@code
 * { "definedNonNull": "Interstellar", "definedNull": null }
 * }</pre>
 * <p>
 * Sa
 */
public sealed interface JsonNullable<T> {

    boolean isDefined();

    default boolean isNull() {
        return isDefined() && value() == null;
    }

    @Nullable
    T value();

    @SuppressWarnings("unchecked")
    static <T> JsonNullable<T> undefined() {
        return JsonNullableUtil.UNDEFINED;
    }

    @SuppressWarnings("unchecked")
    static <T> JsonNullable<T> nullValue() {
        return JsonNullableUtil.NULL;
    }

    static <T> JsonNullable<T> of(T value) {
        Objects.requireNonNull(value, "JsonNullable#of require non-null, but got null");
        return new Defined<>(value);
    }

    static <T> JsonNullable<T> ofNullable(@Nullable T value) {
        return (value == null)
            ? nullValue()
            : new Defined<>(value);
    }

    record Defined<T>(@Nullable T value) implements JsonNullable<T> {

        @Override
        public boolean isDefined() {
            return true;
        }

        @Nullable
        @Override
        public T value() {
            return value;
        }
    }

    record Undefined<T>() implements JsonNullable<T> {

        @Override
        public boolean isDefined() {
            return false;
        }

        @Nullable
        @Override
        public T value() {
            throw new NullPointerException("JsonNullable is undefined");
        }
    }
}

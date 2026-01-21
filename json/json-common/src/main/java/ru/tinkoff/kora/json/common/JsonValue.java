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
 * record Example(JsonValue<String> undefinedNull,
 *                JsonNullable<String> definedNull,
 *                JsonUndefined<String> undefinedNonNull) { }
 * }
 * </pre>
 * →
 * <pre>
 * {@code
 * jsonWriter.toByteArray(
 *      new Example(JsonValue.of("Interstellar"),
 *                  JsonValue.nullValue(),
 *                  JsonValue.undefined())
 *  );
 * }
 * </pre>
 * JsonNullable.undefined() is fully omitted cause it is undefined  →
 * <pre>{@code
 * { "undefinedNull": "Interstellar", "definedNull": null }
 * }</pre>
 */
@SuppressWarnings("unchecked")
public sealed interface JsonValue<T> permits JsonNullable, JsonUndefined {

    boolean isDefined();

    boolean isNull();

    boolean haveValue();

    @Nullable
    T value();

    static <T> JsonUndefined<T> undefined() {
        return JsonUndefined.UNDEFINED;
    }

    static <T> JsonNullable<T> nullValue() {
        return JsonNullable.NULL;
    }

    static <T> JsonValue<T> of(T value) {
        Objects.requireNonNull(value, "JsonValue#of require non-null, but got null");
        return new JsonUndefined<>(value);
    }

    static <T> JsonNullable<T> ofNullable(@Nullable T value) {
        return (value == null)
                ? JsonNullable.NULL
                : new JsonNullable<>(value);
    }

    static <T> JsonUndefined<T> ofUndefined(@Nullable T value) {
        return (value == null)
                ? JsonUndefined.UNDEFINED
                : new JsonUndefined<>(value);
    }
}

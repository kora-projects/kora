package ru.tinkoff.kora.json.common.annotation;

import ru.tinkoff.kora.json.common.JsonValue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотаций позволяет указать какие значения поля допустимы для сериализации в JSON
 * <hr>
 * <b>English</b>: Annotation allows you to specify which field values are valid for serialization in JSON
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Json
 * record Example(String movie, @JsonInclude(IncludeType.NON_NULL) String author) { }
 * }
 * </pre>
 * →
 * <pre>
 * {@code
 * jsonWriter.toByteArray(new Example("Interstellar", null));
 * }
 * </pre>
 * →
 * <pre>{@code
 * { "movie": "Interstellar" }
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonInclude {

    enum IncludeType {

        /**
         * <b>Русский</b>: Указывает что значение должно быть всегда сериализовано (не влияет на {@link JsonValue})
         * <hr>
         * <b>English</b>: Specifies that the value should always be serialized (doesn't affect {@link JsonValue})
         */
        ALWAYS,

        /**
         * <b>Русский</b>: Указывает что значение должно быть сериализовано если оно не равно <i>null</i> (не влияет на {@link JsonValue})
         * <hr>
         * <b>English</b>: Indicates that the value should be serialized if it is <i>non-null</i> (doesn't affect {@link JsonValue})
         */
        NON_NULL,

        /**
         * <b>Русский</b>: Указывает, что значение должно быть сериализовано если оно не равно <i>null</i> и не является пустым. (применяется к {@link JsonValue})
         * <p>
         * Примечание: Может применяться ТОЛЬКО в тех случаях, когда есть возможность проверить тип {@link java.util.Collection} или {@link java.util.Map} во время компиляции.
         * Если тип является общим, эта проверка не может быть применена.
         * <hr>
         * <b>English</b>: Value that indicates that only properties with null value, or what is considered empty, are not to be included. (affects {@link JsonValue})
         * <p>
         * Note: Can be applied ONLY to when it is possible to check {@link java.util.Collection} or {@link java.util.Map} type in compile time
         * If type is generic this check can't be applied
         */
        NON_EMPTY
    }

    /**
     * @return <b>Русский</b>: Указывает уровень допустимых к сериализации значений
     * <hr>
     * <b>English</b>: Specifies the level of values allowed for serialization
     */
    IncludeType value() default IncludeType.NON_NULL;
}

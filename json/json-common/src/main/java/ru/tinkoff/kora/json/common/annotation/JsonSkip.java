package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотаций позволяет указать что поля требует игнорировать при записи/чтении JSON
 * <hr>
 * <b>English</b>: Annotations allows you to specify what fields to ignore when writing/reading JSON
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Json
 * record Example(String movie, @JsonSkip String author) { }
 * }
 * </pre>
 * →
 * <pre>
 * {@code
 * jsonWriter.toByteArray(new Example("Interstellar", "Christopher Nolan"));
 * }
 * </pre>
 * →
 * <pre>{@code
 * { "movie": "Interstellar" }
 * }</pre>
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonSkip { }

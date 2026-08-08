package io.koraframework.database.jdbc.postgres.annotation;

import io.koraframework.common.annotation.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что значение хранится в колонке типа {@code json}.
 * Значение записывается и читается как JSON, для типа значения требуется читатель и писатель JSON.
 * <hr>
 * <b>English</b>: Annotation specifies that the value is stored in a {@code json} column.
 * The value is written and read as JSON, a JSON reader and writer are required for the value type.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @EntityJdbc
 * public record User(long id, @PgJson Details details) {}
 * }
 * </pre>
 *
 * @see PgJsonb
 */
@Tag(PgJson.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PgJson { }

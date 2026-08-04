package io.koraframework.database.jdbc.postgres.annotation;

import io.koraframework.common.annotation.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что значение хранится в колонке типа {@code jsonb}.
 * Отличается от {@link PgJson} типом отправляемого параметра: операторы {@code jsonb} ({@code @>}, {@code ?},
 * {@code jsonb_path_query}) требуют чтобы параметр имел именно тип {@code jsonb}.
 * <hr>
 * <b>English</b>: Annotation specifies that the value is stored in a {@code jsonb} column.
 * Differs from {@link PgJson} in the type of the parameter being sent: {@code jsonb} operators ({@code @>},
 * {@code ?}, {@code jsonb_path_query}) require the parameter to be of the {@code jsonb} type exactly.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @EntityJdbc
 * public record User(long id, @PgJsonb Details details) {}
 * }
 * </pre>
 *
 * @see PgJson
 */
@Tag(PgJsonb.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PgJsonb { }

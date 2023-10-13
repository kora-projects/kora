package ru.tinkoff.kora.database.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает имя таблицы в базе данных для сущности.
 * <hr>
 * <b>English</b>: An annotation specifies table name in the database for an entity model.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @Table("users")
 * public record User(String fullName) {}
 * }
 * </pre>
 *
 * @see Repository
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Table {

    /**
     * @return <b>Русский</b>: Имя в таблицы в базе данных для сущности.
     * <hr>
     * <b>English</b>: Table name in the database for an entity model.
     */
    String value();

    String alias() default "";
}


package ru.tinkoff.kora.database.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает, соотношения имя поля в сущности и имя в колонке таблицы базы данных.
 * <hr>
 * <b>English</b>: Annotation specifies the relationship between the field name in an entity and the name in a column of a database table.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public record User(@Column("fullname") String fullName) {}
 * }
 * </pre>
 *
 * @see Table
 * @see Repository
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.CLASS)
public @interface Column {

    /**
     * @return <b>Русский</b>: Имя поля сущности в базе данных.
     * <hr>
     * <b>English</b>: The name of the entity field in the database.
     */
    String value();
}

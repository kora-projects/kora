package io.koraframework.database.jdbc.postgres.annotation;

import io.koraframework.common.annotation.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что для значения требуется PostgreSQL специфичный конвертер.
 * Указывается над полем сущности либо аргументом метода репозитория для типов, которые сами по себе не являются
 * PostgreSQL специфичными, но имеют PostgreSQL специфичное представление в базе данных.
 * <hr>
 * <b>English</b>: Annotation specifies that a PostgreSQL specific converter is required for the value.
 * Is specified over an entity field or a repository method argument for types that are not PostgreSQL specific
 * themselves, but have a PostgreSQL specific representation in the database.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @EntityJdbc
 * public record User(long id, @Pg List<String> roles, @Pg Duration ttl) {}
 * }
 * </pre>
 */
@Tag(Pg.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Pg { }

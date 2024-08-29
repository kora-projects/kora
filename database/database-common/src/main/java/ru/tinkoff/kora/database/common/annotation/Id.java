package ru.tinkoff.kora.database.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что поле в сущности является первичным ключом в базе данных.
 * <hr>
 * <b>English</b>: The annotation indicates that the field in the entity is a primary key in the database.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 *  public record User(@Id long id) { }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Id {}


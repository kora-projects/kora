package ru.tinkoff.kora.validation.common.annotation;

import ru.tinkoff.kora.validation.common.constraint.factory.NotEmptyValidatorFactory;

import java.lang.annotation.*;

/**
 * Annotated element must not be {@code null} nor empty:
 * <p>
 * Supported types are:
 * <ul>
 * <li>{@code CharSequence} (length of character sequence is evaluated)</li>
 * <li>{@code String} (length of character sequence is evaluated)</li>
 * <li>{@code Iterable} (collection size is evaluated)</li>
 * <li>{@code Collection} (collection size is evaluated)</li>
 * <li>{@code List} (collection size is evaluated)</li>
 * <li>{@code Set} (collection size is evaluated)</li>
 * <li>{@code Map} (map size is evaluated)</li>
 * </ul>
 */
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(NotEmptyValidatorFactory.class)
public @interface NotEmpty {
}

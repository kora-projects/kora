package ru.tinkoff.kora.validation.common.annotation;

import ru.tinkoff.kora.validation.common.constraint.factory.NotEmptyValidatorFactory;

import java.lang.annotation.*;

/**
 * Annotated element must not be {@code null} nor empty:
 * <p>
 * Supported types are:
 * <ul>
 * <li>{@code CharSequence} (length of character sequence is evaluated)</li>
 * <li>{@code Collection} (collection size is evaluated)</li>
 * <li>{@code Map} (map size is evaluated)</li>
 * </ul>
 */
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.FIELD})
@ValidatedBy(NotEmptyValidatorFactory.class)
public @interface NotEmpty {
}

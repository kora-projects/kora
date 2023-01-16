package ru.tinkoff.kora.validation.common.annotation;

import java.lang.annotation.*;

/**
 * Indicates that entity should be validated and thus {@link ru.tinkoff.kora.validation.common.Validator} for such entity will be provided
 */
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE, ElementType.FIELD})
public @interface Validated {

}

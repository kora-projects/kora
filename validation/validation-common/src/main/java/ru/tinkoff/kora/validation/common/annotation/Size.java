package ru.tinkoff.kora.validation.common.annotation;

import org.jetbrains.annotations.Range;
import ru.tinkoff.kora.validation.common.constraint.factory.SizeValidatorFactory;

import java.lang.annotation.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Validates {@link List}, {@link Collection}, {@link Map}, {@link String} or {@link CharSequence} size
 */
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(SizeValidatorFactory.class)
public @interface Size {

    /**
     * @return minimum value should have (inclusive)
     */
    @Range(from = 0, to = Integer.MAX_VALUE)
    int min() default 0;

    /**
     * @return maximum value should have (inclusive)
     */
    @Range(from = 0, to = Integer.MAX_VALUE)
    int max();
}

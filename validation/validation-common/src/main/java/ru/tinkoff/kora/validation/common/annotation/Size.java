package ru.tinkoff.kora.validation.common.annotation;

import ru.tinkoff.kora.validation.common.constraint.factory.SizeValidatorFactory;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.FIELD})
@ValidatedBy(SizeValidatorFactory.class)
public @interface Size {

    /**
     * @return minimum value should have (inclusive)
     */
    int min() default 0;

    /**
     * @return maximum value should have (inclusive)
     */
    int max();
}

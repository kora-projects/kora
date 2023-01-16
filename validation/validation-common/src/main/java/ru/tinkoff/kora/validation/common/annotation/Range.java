package ru.tinkoff.kora.validation.common.annotation;

import ru.tinkoff.kora.validation.common.constraint.factory.RangeValidatorFactory;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.FIELD})
@ValidatedBy(RangeValidatorFactory.class)
public @interface Range {

    /**
     * @return minimum value should have (boundary rule {@link #boundary()})
     */
    double from();

    /**
     * @return maximum value should have (boundary rule {@link #boundary()})
     */
    double to();

    enum Boundary {
        EXCLUSIVE_EXCLUSIVE,
        INCLUSIVE_EXCLUSIVE,
        EXCLUSIVE_INCLUSIVE,
        INCLUSIVE_INCLUSIVE,
    }

    Boundary boundary() default Boundary.INCLUSIVE_INCLUSIVE;
}

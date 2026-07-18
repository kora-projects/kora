package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.PastValidatorFactory;

import java.lang.annotation.*;
import java.time.*;

/**
 * Validates {@link LocalDate}, {@link LocalDateTime}, {@link Instant}, {@link OffsetDateTime}, {@link ZonedDateTime} value to be in the past
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(PastValidatorFactory.class)
public @interface Past {

}

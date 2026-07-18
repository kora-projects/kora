package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.PastOrPresentValidatorFactory;

import java.lang.annotation.*;
import java.time.*;

/**
 * Validates {@link LocalDate}, {@link LocalDateTime}, {@link Instant}, {@link OffsetDateTime}, {@link ZonedDateTime} value to be in the past or present
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(PastOrPresentValidatorFactory.class)
public @interface PastOrPresent {

}

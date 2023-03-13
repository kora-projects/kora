package ru.tinkoff.kora.validation.common.annotation;

import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.ValidatorFactory;

import java.lang.annotation.*;

/**
 * Indicates that annotation is used for validation and providers factory that instantiates {@link Validator}
 */
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.ANNOTATION_TYPE})
public @interface ValidatedBy {

    Class<? extends ValidatorFactory> value();
}

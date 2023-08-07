package ru.tinkoff.kora.resilient.timeout.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.timeout.TimeoutConfig;
import ru.tinkoff.kora.resilient.timeout.TimeoutExhaustedException;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link ru.tinkoff.kora.resilient.timeout.Timeout} to a specific method
 * When applied to method, method may throw {@link TimeoutExhaustedException} when all timeout occured
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Timeout {

    /**
     * @see TimeoutConfig
     * @return the name of the Timeout config path
     */
    String value();
}

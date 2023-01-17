package ru.tinkoff.kora.resilient.timeout.annotation;

import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.timeout.Timeouter;

import java.lang.annotation.*;

/**
 * Annotation allow applying {@link Timeouter} to a specific method
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Timeout {

    /**
     * @return the name of the Timeout config path
     */
    String value();
}

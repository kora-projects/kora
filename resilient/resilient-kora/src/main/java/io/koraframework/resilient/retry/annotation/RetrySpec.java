package io.koraframework.resilient.retry.annotation;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.TYPE})
public @interface RetrySpec {

    /**
     * @return path for Retry config
     */
    String value();
}

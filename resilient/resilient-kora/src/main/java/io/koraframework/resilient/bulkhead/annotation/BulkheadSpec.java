package io.koraframework.resilient.bulkhead.annotation;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {
        ElementType.TYPE
})
public @interface BulkheadSpec {

    /**
     * @return path for Bulkhead config
     */
    String value();
}

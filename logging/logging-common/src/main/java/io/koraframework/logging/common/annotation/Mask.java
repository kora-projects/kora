package io.koraframework.logging.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mask {

    String value() default "***";

    Mode mode() default Mode.FULL;

    int keep() default 4;

    enum Mode {
        FULL,
        KEEP_LAST,
        KEEP_FIRST
    }
}

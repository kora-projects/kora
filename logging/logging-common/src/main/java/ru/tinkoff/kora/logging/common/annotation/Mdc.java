package ru.tinkoff.kora.logging.common.annotation;

import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

@AopAnnotation
@Repeatable(Mdc.MdcContainer.class)
@Target({METHOD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mdc {

    String key() default "";

    String value() default "";

    boolean global() default false;

    @AopAnnotation
    @Target(METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface MdcContainer {
        Mdc[] value();
    }
}

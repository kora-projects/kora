package ru.tinkoff.kora.logging.common.annotation;

import org.slf4j.event.Level;
import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

@AopAnnotation
@Target({METHOD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    Level value() default Level.INFO;

    @AopAnnotation
    @Target(METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface in {
        Level value() default Level.INFO;
    }

    @AopAnnotation
    @Target(METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface out {
        Level value() default Level.INFO;
    }

    @Target(METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface result {
        Level value() default Level.DEBUG;
    }

    @Target({PARAMETER, METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface off {}
}

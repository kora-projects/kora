package ru.tinkoff.kora.config.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface ConfigRoot {
    /**
     * @return List of KoraApp applications
     */
    Class<?>[] value() default {};
}

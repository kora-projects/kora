package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that class/interface is part of sealed class and annotated type is associated with {@link #value()} field value, works in conjunctions with top {@link JsonDiscriminatorField} annotation
 *
 * @see JsonDiscriminatorField
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface JsonDiscriminatorValue {

    /**
     * @return value that indicates annotated type
     */
    String value();
}

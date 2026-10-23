package io.koraframework.openfeature.annotation;

import dev.openfeature.sdk.FlagValueType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides registered SDK flag type. Changing the inferred type requires a custom flag mapper.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenfeatureType {
    FlagValueType value();
}

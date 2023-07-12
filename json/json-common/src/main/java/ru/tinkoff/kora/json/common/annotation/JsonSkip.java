package ru.tinkoff.kora.json.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that annotated field will be ignored and not serialized into JSON
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.CLASS)
public @interface JsonSkip {
}

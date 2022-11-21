package ru.tinkoff.kora.cache.annotation;

import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see CacheInvalidate
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AopAnnotation
public @interface CacheInvalidates {

    CacheInvalidate[] value();
}

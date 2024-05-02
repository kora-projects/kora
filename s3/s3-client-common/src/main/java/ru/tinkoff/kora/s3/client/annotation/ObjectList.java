package ru.tinkoff.kora.s3.client.annotation;

import org.jetbrains.annotations.Range;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface ObjectList {

    String prefix() default "";

    @Range(from = 1, to = 1000)
    int limit() default 1000;
}

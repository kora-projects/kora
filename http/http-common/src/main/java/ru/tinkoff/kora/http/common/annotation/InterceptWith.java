package ru.tinkoff.kora.http.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(InterceptWith.InterceptWithContainer.class)
public @interface InterceptWith {

    Class<?> value();

    Tag tag() default @Tag({});

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface InterceptWithContainer {
        InterceptWith[] value();
    }
}

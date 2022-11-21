package ru.tinkoff.kora.http.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(InterceptWith.InterceptWithContainer.class)
public @interface InterceptWith {
    Class<?> value();

    Tag tag() default @Tag({});

    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface InterceptWithContainer {
        InterceptWith[] value();
    }
}

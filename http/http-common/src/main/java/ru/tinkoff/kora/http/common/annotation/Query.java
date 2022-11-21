package ru.tinkoff.kora.http.common.annotation;

public @interface Query {
    String value() default  "";
}

package ru.tinkoff.kora.http.common.annotation;

public @interface Header {
    String value() default  "";
}

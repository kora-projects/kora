package ru.tinkoff.kora.http.common.annotation;

public @interface HttpRoute {

    String method();

    String path();
}

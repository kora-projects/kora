package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;

public class ParentTestController<T> {
    @HttpRoute(method = HttpMethod.GET, path = "/parent")
    public T someMethod() {
        return null;
    }

    @HttpRoute(method = HttpMethod.POST, path = "/parent-param")
    public void someMethodWithParam(T param) {
    }


}

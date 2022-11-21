package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import javax.annotation.Nullable;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerWithNullableResult {

    @HttpRoute(method = GET, path = "/getNullable")
    @Nullable
    public String testNullable() {
        return null;
    }

}

package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;
import static ru.tinkoff.kora.http.common.HttpMethod.POST;

@HttpController("/root")
public class TestControllerWithPrefix {
    @HttpRoute(method = GET, path = "/test")
    public String test() {
        return "";
    }

    @HttpRoute(method = POST, path = "")
    public String testRoot() {
        return "";
    }

}

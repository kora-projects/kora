package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerWithPaths {
    @HttpRoute(method = GET, path = "/swagger.yaml")
    public String swaggerYaml() {
        return "";
    }
}

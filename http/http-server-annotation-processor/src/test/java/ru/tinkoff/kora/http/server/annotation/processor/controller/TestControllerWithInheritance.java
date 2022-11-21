package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

@HttpController("/base")
public class TestControllerWithInheritance extends ParentTestController<String> {

    @HttpRoute(method = HttpMethod.GET, path = "/child")
    public String someOtherMethod() {
        return "child";
    }
}

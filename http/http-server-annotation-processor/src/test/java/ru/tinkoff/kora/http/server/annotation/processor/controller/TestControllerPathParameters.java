package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerPathParameters {

    @HttpRoute(method = GET, path = "/pathString/{string}")
    public String pathString(@Path String string) {
        return string;
    }

    @HttpRoute(method = GET, path = "/pathStringWithName/{stringWithName}")
    void pathStringWithName(@Path("stringWithName") String string) {
    }

    @HttpRoute(method = GET, path = "/pathInteger/{value}")
    void pathInteger(@Path int value) {
    }

    @HttpRoute(method = GET, path = "/pathIntegerObject/{value}")
    void pathIntegerObject(@Path Integer value) {
    }

    @HttpRoute(method = GET, path = "/pathLong/{value}")
    void pathLong(@Path long value) {
    }

    @HttpRoute(method = GET, path = "/pathLongObject/{value}")
    void pathLongObject(@Path Long value) {
    }

    @HttpRoute(method = GET, path = "/pathDouble/{value}")
    void pathDouble(@Path double value) {
    }

    @HttpRoute(method = GET, path = "/pathDoubleObject/{value}")
    void pathDoubleObject(@Path Double value) {
    }

    @HttpRoute(method = GET, path = "/pathEnum/{value}")
    void pathEnum(@Path TestEnum value) {
    }
}

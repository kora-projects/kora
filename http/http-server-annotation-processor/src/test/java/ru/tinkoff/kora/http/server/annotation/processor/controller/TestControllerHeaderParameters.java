package ru.tinkoff.kora.http.server.annotation.processor.controller;

import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

import static ru.tinkoff.kora.http.common.HttpMethod.GET;

@HttpController
public class TestControllerHeaderParameters {
    /*
    Headers: String, Integer, List<String>, List<Integer>
     */

    @HttpRoute(method = GET, path = "/headerString")
    public String headerString(@Header(value = "string-header") String string) {
        return string;
    }

    @HttpRoute(method = GET, path = "/headerNullableString")
    void headerNullableString(@Header @Nullable String string) {
    }

    @HttpRoute(method = GET, path = "/headerOptionalString")
    void headerNullableString(@Header Optional<String> string) {
    }

    @HttpRoute(method = GET, path = "/headerStringList")
    void headerNullableString(@Header List<String> string) {
    }

    @HttpRoute(method = GET, path = "/headerInteger")
    public void headerInteger(@Header(value = "integer-header") Integer integer) {
    }

    @HttpRoute(method = GET, path = "/headerNullableInteger")
    public void headerNullableInteger(@Header(value = "integer-header") @Nullable Integer integer) {
    }

    @HttpRoute(method = GET, path = "/headerOptionalInteger")
    public void headerOptionalInteger(@Header(value = "integer-header") Optional<Integer> integer) {
    }

    @HttpRoute(method = GET, path = "/headerIntegerList")
    public void headerStringList(@Header(value = "integer-header") List<Integer> integers) {
    }
}

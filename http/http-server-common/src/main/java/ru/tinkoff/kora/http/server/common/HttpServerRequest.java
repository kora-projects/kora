package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface HttpServerRequest {
    String method();

    String path();

    String route();

    HttpHeaders headers();

    Map<String, ? extends Collection<String>> queryParams();

    Map<String, String> pathParams();

    HttpBodyInput body();
}

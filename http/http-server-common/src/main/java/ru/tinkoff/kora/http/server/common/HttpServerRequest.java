package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpInBody;

import java.util.Collection;
import java.util.Map;

public interface HttpServerRequest {
    String method();

    String path();

    String route();

    HttpHeaders headers();

    Map<String, ? extends Collection<String>> queryParams();

    Map<String, String> pathParams();

    HttpInBody body();
}

package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface HttpServerRequest {

    String method();

    String path();

    String route();

    HttpHeaders headers();

    List<Cookie> cookies();

    Map<String, ? extends Collection<String>> queryParams();

    Map<String, String> pathParams();

    HttpBodyInput body();
}

package ru.tinkoff.kora.http.server.common.router;

import ru.tinkoff.kora.http.common.body.HttpInBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface PublicApiRequest {
    String method();

    String path();

    String hostName();

    String scheme();

    HttpHeaders headers();

    Map<String, ? extends Collection<String>> queryParams();

    HttpInBody body();
}

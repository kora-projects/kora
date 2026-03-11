package io.koraframework.http.server.common.router;

import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface PublicApiRequest {
    String method();

    String path();

    String hostName();

    String scheme();

    HttpHeaders headers();

    Map<String, ? extends Collection<String>> queryParams();

    HttpBodyInput body();

    long requestStartTime();
}

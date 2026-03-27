package io.koraframework.http.server.common.request;

import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.header.HttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record SimpleHttpServerRequest(String host,
                                      String scheme,
                                      String method,
                                      String path,
                                      @Nullable String pathTemplate,
                                      Map<String, String> pathParams,
                                      Map<String, List<String>> queryParams,
                                      HttpHeaders headers,
                                      List<Cookie> cookies,
                                      HttpBodyInput body,
                                      long requestStartTimeInNanos) implements HttpServerRequest {

    @Override
    public String toString() {
        return method + " " + path;
    }
}

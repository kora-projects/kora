package io.koraframework.http.server.common;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface HttpServerRequest {

    String method();

    String path();

    /**
     * @return may be nullable if route not matched to any controller
     */
    @Nullable
    String route();

    HttpHeaders headers();

    List<Cookie> cookies();

    Map<String, ? extends Collection<String>> queryParams();

    Map<String, String> pathParams();

    HttpBodyInput body();
}

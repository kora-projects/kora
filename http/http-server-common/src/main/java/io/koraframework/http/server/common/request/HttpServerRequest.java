package io.koraframework.http.server.common.request;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.List;
import java.util.Map;

public interface HttpServerRequest {

    String host();

    String scheme();

    String method();

    String path();

    /**
     * @return may be nullable if route not matched to any handler
     */
    @Nullable
    String pathTemplate();

    Map<String, String> pathParams();

    HttpHeaders headers();

    List<Cookie> cookies();

    Map<String, List<String>> queryParams();

    HttpBodyInput body();

    long requestStartTimeInNanos();

    default HttpServerRequestBuilder toBuilder() {
        return new HttpServerRequestBuilderImpl(this);
    }
}

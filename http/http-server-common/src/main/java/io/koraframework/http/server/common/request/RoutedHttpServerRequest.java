package io.koraframework.http.server.common.request;

import io.koraframework.http.common.body.*;
import io.koraframework.http.common.cookie.*;
import io.koraframework.http.common.header.*;
import io.koraframework.http.server.common.router.*;
import org.jspecify.annotations.*;

import java.util.*;

public class RoutedHttpServerRequest implements HttpServerRequest {

    private final UnroutedHttpRequest unroutedHttpRequest;
    private final Map<String, String> pathParams;
    private final String pathTemplate;
    private HttpHeaders headers;
    private Map<String, List<String>> queryParams;
    private List<Cookie> cookies;

    public RoutedHttpServerRequest(
        UnroutedHttpRequest unroutedHttpRequest,
        Map<String, String> pathParams,
        @Nullable String pathTemplate) {
        this.unroutedHttpRequest = unroutedHttpRequest;
        this.pathParams = pathParams;
        this.pathTemplate = pathTemplate;
    }

    @Override
    public String host() {
        return unroutedHttpRequest.host();
    }

    @Override
    public String scheme() {
        return unroutedHttpRequest.scheme();
    }

    @Override
    public String method() {
        return unroutedHttpRequest.method();
    }

    @Override
    public String path() {
        return unroutedHttpRequest.path();
    }

    @Override
    public String pathTemplate() {
        return pathTemplate;
    }

    @Override
    public HttpHeaders headers() {
        var headers = this.headers;
        if (headers == null) {
            this.headers = headers = this.unroutedHttpRequest.headers();
        }
        return headers;
    }

    @Override
    public List<Cookie> cookies() {
        var cookies = this.cookies;
        if (cookies == null) {
            var cookie = headers().getAll("Cookie");
            if (cookie == null || cookie.isEmpty()) {
                this.cookies = cookies = Collections.emptyList();
            } else {
                this.cookies = cookies = new ArrayList<>();
                Cookies.parseRequestCookies(200, false, cookie, cookies);
            }
        }
        return cookies;
    }

    @Override
    public Map<String, List<String>> queryParams() {
        var queryParams = this.queryParams;
        if (queryParams == null) {
            this.queryParams = queryParams = this.unroutedHttpRequest.queryParams();
        }
        return queryParams;
    }

    @Override
    public Map<String, String> pathParams() {
        return this.pathParams;
    }

    @Override
    public HttpBodyInput body() {
        return this.unroutedHttpRequest.body();
    }

    @Override
    public long requestStartTimeInNanos() {
        return unroutedHttpRequest.requestStartTimeInNanos();
    }

    @Override
    public String toString() {
        return method() + " " + path();
    }
}

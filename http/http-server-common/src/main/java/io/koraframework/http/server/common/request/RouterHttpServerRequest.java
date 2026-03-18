package io.koraframework.http.server.common.request;

import io.koraframework.http.server.common.router.HttpRouterRequest;
import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.cookie.Cookies;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouterHttpServerRequest implements HttpServerRequest {

    private final HttpRouterRequest httpRouterRequest;
    private final Map<String, String> pathParams;
    private final String pathTemplate;
    private HttpHeaders headers;
    private Map<String, List<String>> queryParams;
    private List<Cookie> cookies;

    public RouterHttpServerRequest(HttpRouterRequest httpRouterRequest, Map<String, String> pathParams, @Nullable String pathTemplate) {
        this.httpRouterRequest = httpRouterRequest;
        this.pathParams = pathParams;
        this.pathTemplate = pathTemplate;
    }

    @Override
    public String host() {
        return httpRouterRequest.hostName();
    }

    @Override
    public String scheme() {
        return httpRouterRequest.scheme();
    }

    @Override
    public String method() {
        return httpRouterRequest.method();
    }

    @Override
    public String path() {
        return httpRouterRequest.path();
    }

    @Override
    public String pathTemplate() {
        return this.pathTemplate;
    }

    @Override
    public HttpHeaders headers() {
        var headers = this.headers;
        if (headers == null) {
            this.headers = headers = this.httpRouterRequest.headers();
        }
        return headers;
    }

    @Override
    public List<Cookie> cookies() {
        var cookies = this.cookies;
        if (cookies == null) {
            cookies = this.cookies = new ArrayList<>();
            var cookie = this.headers().getAll("Cookie");
            if (cookie != null) {
                Cookies.parseRequestCookies(200, false, cookie, cookies);
            }
        }
        return this.cookies;
    }

    @Override
    public Map<String, List<String>> queryParams() {
        var queryParams = this.queryParams;
        if (queryParams == null) {
            this.queryParams = queryParams = this.httpRouterRequest.queryParams();
        }
        return queryParams;
    }

    @Override
    public Map<String, String> pathParams() {
        return this.pathParams;
    }

    @Override
    public HttpBodyInput body() {
        return this.httpRouterRequest.body();
    }

    @Override
    public long requestStartTimeInNanos() {
        return httpRouterRequest.requestStartTime();
    }

    @Override
    public String toString() {
        return method() + " " + path();
    }
}

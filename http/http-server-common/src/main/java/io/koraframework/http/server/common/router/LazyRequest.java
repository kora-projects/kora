package io.koraframework.http.server.common.router;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.cookie.Cookies;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.HttpServerRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class LazyRequest implements HttpServerRequest {
    private final PublicApiRequest publicApiRequest;
    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final String route;
    private HttpHeaders headers;
    private Map<String, ? extends Collection<String>> queryParams;
    private List<Cookie> cookies;

    public LazyRequest(PublicApiRequest publicApiRequest, Map<String, String> pathParams, @Nullable String routeTemplate) {
        this.publicApiRequest = publicApiRequest;
        this.method = publicApiRequest.method();
        this.path = publicApiRequest.path();
        this.pathParams = pathParams;
        this.route = routeTemplate;
    }

    @Override
    public String method() {
        return this.method;
    }

    @Override
    public String path() {
        return this.path;
    }

    @Override
    public String route() {
        return this.route;
    }

    @Override
    public HttpHeaders headers() {
        var headers = this.headers;
        if (headers == null) {
            this.headers = headers = this.publicApiRequest.headers();
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
    public Map<String, ? extends Collection<String>> queryParams() {
        var queryParams = this.queryParams;
        if (queryParams == null) {
            this.queryParams = queryParams = this.publicApiRequest.queryParams();
        }
        return queryParams;
    }

    @Override
    public Map<String, String> pathParams() {
        return this.pathParams;
    }

    @Override
    public HttpBodyInput body() {
        return this.publicApiRequest.body();
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}

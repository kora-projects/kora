package io.koraframework.http.server.common.request;

import io.koraframework.http.server.common.router.UnroutedHttpRequest;
import org.jspecify.annotations.Nullable;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.cookie.Cookies;
import io.koraframework.http.common.header.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoutedHttpServerRequest implements HttpServerRequest {

    private final UnroutedHttpRequest unroutedHttpRequest;
    private final Map<String, String> pathParams;
    private final String pathTemplate;
    private HttpHeaders headers;
    private Map<String, List<String>> queryParams;
    private List<Cookie> cookies;

    public RoutedHttpServerRequest(UnroutedHttpRequest unroutedHttpRequest, Map<String, String> pathParams, @Nullable String pathTemplate) {
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
        return this.pathTemplate;
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

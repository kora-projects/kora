package io.koraframework.http.server.common.request;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.header.HttpHeadersImpl;
import io.koraframework.http.common.header.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpServerRequestBuilderImpl implements HttpServerRequestBuilder {

    private final String host;
    private final String scheme;
    private final String method;
    private final String path;
    @Nullable
    private final String pathTemplate;
    private HttpHeaders headers;
    private boolean headersOwned;
    private final Map<String, String> pathParams;
    private Map<String, List<String>> queryParams;
    private boolean queryParamsOwned;
    private final List<Cookie> cookies;
    private final long requestStartTimeInNanos;

    private HttpBodyInput body = HttpBody.empty();

    public HttpServerRequestBuilderImpl(HttpServerRequest request) {
        this.host = request.host();
        this.scheme = request.scheme();
        this.method = request.method();
        this.path = request.path();
        this.pathTemplate = request.pathTemplate();
        this.pathParams = request.pathParams();
        this.headers = request.headers();
        this.cookies = request.cookies();
        this.queryParams = request.queryParams();
        this.requestStartTimeInNanos = request.requestStartTimeInNanos();
    }

    @Override
    public HttpServerRequest build() {
        var queries = this.queryParamsOwned
            ? copyQueryParams(this.queryParams)
            : this.queryParams;

        return new SimpleHttpServerRequest(this.host, this.scheme, this.method, this.path, this.pathTemplate,
            this.pathParams, queries, this.headers, this.cookies, this.body, this.requestStartTimeInNanos);
    }

    @Override
    public HttpServerRequestBuilder queryParam(String name) {
        this.mutableQueryParams().computeIfAbsent(name, k -> new ArrayList<>());
        return this;
    }

    @Override
    public HttpServerRequestBuilder queryParam(String name, String value) {
        this.mutableQueryParams().computeIfAbsent(name, k -> new ArrayList<>(1)).add(value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder queryParamRemove(String name) {
        if (this.queryParams.isEmpty()) {
            return this;
        }
        this.mutableQueryParams().remove(name);
        return this;
    }

    @Override
    public HttpServerRequestBuilder header(String name, String value) {
        this.mutableHeaders().set(name, value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder header(String name, List<String> value) {
        this.mutableHeaders().set(name, value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder headerRemove(String name) {
        if (this.headers.isEmpty()) {
            return this;
        }
        this.mutableHeaders().remove(name);
        return this;
    }

    @Override
    public HttpServerRequestBuilder body(HttpBodyInput body) {
        this.body = body;
        return this;
    }

    private MutableHttpHeaders mutableHeaders() {
        if (this.headersOwned && this.headers instanceof MutableHttpHeaders mutableHeaders) {
            return mutableHeaders;
        }
        var mutableHeaders = this.headersOwned
            ? this.headers.toMutable()
            : new HttpHeadersImpl(this.headers);
        this.headers = mutableHeaders;
        this.headersOwned = true;
        return mutableHeaders;
    }

    private Map<String, List<String>> mutableQueryParams() {
        if (!this.queryParamsOwned) {
            this.queryParams = copyQueryParams(this.queryParams);
            this.queryParamsOwned = true;
        }
        return this.queryParams;
    }

    private static Map<String, List<String>> copyQueryParams(Map<String, List<String>> queryParams) {
        if (queryParams.isEmpty()) {
            return new LinkedHashMap<>(calculateHashMapCapacity(4));
        }
        var copy = new LinkedHashMap<String, List<String>>(calculateHashMapCapacity(queryParams.size()));
        for (var entry : queryParams.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static int calculateHashMapCapacity(int size) {
        return Math.max((int) (size / 0.75f) + 1, 16);
    }

    @Override
    public String toString() {
        return "HttpServerRequestBuilder{method=" + method +
               (pathTemplate == null ? "" : ", pathTemplate=" + pathTemplate) +
               ", (pathMatched=" + path +
               "), queries=" + queryParams +
               ", headers=" + headers +
               ", body=" + body +
               '}';
    }
}

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
        host = request.host();
        scheme = request.scheme();
        method = request.method();
        path = request.path();
        pathTemplate = request.pathTemplate();
        pathParams = request.pathParams();
        headers = request.headers();
        cookies = request.cookies();
        queryParams = request.queryParams();
        requestStartTimeInNanos = request.requestStartTimeInNanos();
    }

    @Override
    public HttpServerRequest build() {
        var queries = queryParamsOwned
            ? copyQueryParams(queryParams)
            : queryParams;

        return new SimpleHttpServerRequest(host, scheme, method, path, pathTemplate,
            pathParams, queries, headers, cookies, body, requestStartTimeInNanos);
    }

    @Override
    public HttpServerRequestBuilder queryParam(String name) {
        var params = mutableQueryParams();
        var list = params.get(name);
        if (list == null) {
            list = new ArrayList<>();
            params.put(name, list);
        }
        return this;
    }

    @Override
    public HttpServerRequestBuilder queryParam(String name, String value) {
        var params = mutableQueryParams();
        var list = params.get(name);
        if (list == null) {
            list = new ArrayList<>(1);
            params.put(name, list);
        }
        list.add(value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder queryParamRemove(String name) {
        if (queryParams.isEmpty()) {
            return this;
        }
        mutableQueryParams().remove(name);
        return this;
    }

    @Override
    public HttpServerRequestBuilder header(String name, String value) {
        mutableHeaders().set(name, value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder header(String name, List<String> value) {
        mutableHeaders().set(name, value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder headerRemove(String name) {
        if (headers.isEmpty()) {
            return this;
        }
        mutableHeaders().remove(name);
        return this;
    }

    @Override
    public HttpServerRequestBuilder body(HttpBodyInput body) {
        this.body = body;
        return this;
    }

    private MutableHttpHeaders mutableHeaders() {
        if (headersOwned && headers instanceof MutableHttpHeaders mutableHeaders) {
            return mutableHeaders;
        }
        var mutableHeaders = headersOwned
            ? headers.toMutable()
            : new HttpHeadersImpl(headers);
        headers = mutableHeaders;
        headersOwned = true;
        return mutableHeaders;
    }

    private Map<String, List<String>> mutableQueryParams() {
        if (!queryParamsOwned) {
            queryParams = copyQueryParams(queryParams);
            queryParamsOwned = true;
        }
        return queryParams;
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

package io.koraframework.http.server.common.request;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.header.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpServerRequestBuilderImpl implements HttpServerRequestBuilder {

    private final String host;
    private final String scheme;
    private final String method;
    private final String path;
    @Nullable
    private final String pathTemplate;
    private final MutableHttpHeaders headers;
    private final Map<String, String> pathParams;
    private final List<QueryParam> queryParams;
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
        this.headers = request.headers().toMutable();
        this.cookies = request.cookies();
        this.queryParams = request.queryParams().entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(v -> new QueryParam(e.getKey(), v)))
            .collect(Collectors.toList());
        this.requestStartTimeInNanos = request.requestStartTimeInNanos();
    }

    @Override
    public HttpServerRequest build() {
        Map<String, List<String>> queries = new LinkedHashMap<>();
        for (var queryParam : this.queryParams) {
            var values = queries.computeIfAbsent(queryParam.name(), k -> new ArrayList<>());
            if (queryParam.value() != null) {
                values.add(queryParam.value());
            }
        }

        return new SimpleHttpServerRequest(this.host, this.scheme, this.method, this.path, this.pathTemplate,
            this.pathParams, queries, this.headers, this.cookies, this.body, this.requestStartTimeInNanos);
    }

    @Override
    public HttpServerRequestBuilder queryParam(String name) {
        this.queryParams.add(new QueryParam(name, null));
        return this;
    }

    @Override
    public HttpServerRequestBuilder queryParam(String name, String value) {
        this.queryParams.add(new QueryParam(name, value));
        return this;
    }

    @Override
    public HttpServerRequestBuilder queryParamRemove(String name) {
        this.queryParams.removeIf(entry -> entry.name().equals(name));
        return this;
    }

    @Override
    public HttpServerRequestBuilder header(String name, String value) {
        this.headers.set(name, value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder header(String name, List<String> value) {
        this.headers.set(name, value);
        return this;
    }

    @Override
    public HttpServerRequestBuilder headerRemove(String name) {
        this.headers.remove(name);
        return this;
    }

    @Override
    public HttpServerRequestBuilder body(HttpBodyInput body) {
        this.body = body;
        return this;
    }

    private record QueryParam(String name, @Nullable String value) {}

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

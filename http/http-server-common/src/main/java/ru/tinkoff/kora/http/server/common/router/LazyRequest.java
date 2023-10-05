package ru.tinkoff.kora.http.server.common.router;

import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.util.Collection;
import java.util.Map;

class LazyRequest implements HttpServerRequest {
    private final PublicApiRequest publicApiRequest;
    private final String method;
    private final String path;
    private final Map<String, String> pathParams;
    private final String route;
    private HttpHeaders headers;
    private Map<String, ? extends Collection<String>> queryParams;

    LazyRequest(PublicApiRequest publicApiRequest, Map<String, String> pathParams, String routeTemplate) {
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
}

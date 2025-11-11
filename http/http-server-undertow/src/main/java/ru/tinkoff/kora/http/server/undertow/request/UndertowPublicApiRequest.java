package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.server.HttpServerExchange;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpHeaders;

import java.io.IOException;
import java.util.*;

public class UndertowPublicApiRequest implements PublicApiRequest {
    private final HttpServerExchange exchange;
    private final String method;
    private final String path;
    private volatile HttpBodyInput body;
    private final UndertowHttpHeaders headers;

    public UndertowPublicApiRequest(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.method = exchange.getRequestMethod().toString();
        this.path = exchange.getRelativePath();
        this.headers = new UndertowHttpHeaders(exchange.getRequestHeaders());
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
    public String hostName() {
        return this.exchange.getHostName();
    }

    @Override
    public String scheme() {
        return this.exchange.getRequestScheme();
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public Map<String, ? extends Collection<String>> queryParams() {
        return queryParams(this.exchange);
    }

    @Override
    public HttpBodyInput body() {
        var b = this.body;
        if (b != null) {
            return b;
        }
        try {
            b = this.getContent(exchange);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this.body = b;
    }

    @Override
    public long requestStartTime() {
        return exchange.getRequestStartTime();
    }

    private static Map<String, List<String>> queryParams(HttpServerExchange httpServerExchange) {
        var undertowQueryParams = httpServerExchange.getQueryParameters();
        if (undertowQueryParams.isEmpty()) {
            return Map.of();
        }

        var queryParams = new LinkedHashMap<String, List<String>>(undertowQueryParams.size());
        for (var entry : undertowQueryParams.entrySet()) {
            var key = entry.getKey();
            var value = new ArrayList<String>(entry.getValue().size());
            for (var it : entry.getValue()) {
                if (!it.isEmpty()) {
                    value.add(it);
                }
            }
            queryParams.put(key, value);
        }
        return Collections.unmodifiableMap(queryParams);
    }

    private HttpBodyInput getContent(HttpServerExchange exchange) throws IOException {
        if (exchange.isRequestComplete()) {
            // request body is empty
            return HttpBody.empty();
        }
        return new UndertowRequestHttpBody(exchange);
    }
}

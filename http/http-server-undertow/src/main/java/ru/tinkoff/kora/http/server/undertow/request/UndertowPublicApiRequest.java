package ru.tinkoff.kora.http.server.undertow.request;

import io.undertow.server.Connectors;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.router.PublicApiRequest;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpHeaders;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class UndertowPublicApiRequest implements PublicApiRequest {
    private final HttpServerExchange exchange;
    private final Context context;
    private final String method;
    private final String path;
    private volatile HttpBodyInput body;
    private final UndertowHttpHeaders headers;

    public UndertowPublicApiRequest(HttpServerExchange exchange, Context context) {
        this.exchange = exchange;
        this.method = exchange.getRequestMethod().toString();
        this.path = exchange.getRelativePath();
        this.headers = new UndertowHttpHeaders(exchange.getRequestHeaders());
        this.context = context;
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

    private static Map<String, List<String>> queryParams(HttpServerExchange httpServerExchange) {
        var undertowQueryParams = httpServerExchange.getQueryParameters();
        var queryParams = new HashMap<String, List<String>>(undertowQueryParams.size());
        for (var entry : undertowQueryParams.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue().stream()
                .filter(Predicate.not(String::isEmpty))
                .toList();
            queryParams.put(key, List.copyOf(value));
        }
        return Map.copyOf(queryParams);
    }

    private HttpBodyInput getContent(HttpServerExchange exchange) throws IOException {
        if (exchange.isRequestComplete()) {
            // request body is empty
            return HttpBody.empty();
        }
        if (!exchange.isInIoThread()) {
            return new UndertowRequestHttpBody(context, exchange);
        }
        try (var pooled = exchange.getConnection().getByteBufferPool().allocate()) {
            var buffer = pooled.getBuffer();
            buffer.clear();
            Connectors.resetRequestChannel(exchange);
            var channel = exchange.getRequestChannel();
            var res = channel.read(buffer);
            if (res == -1) {
                return HttpBody.empty();
            } else if (res == 0) {
                return new UndertowRequestHttpBody(context, exchange);
            }
            buffer.flip();
            var firstData = new byte[buffer.remaining()];
            buffer.get(firstData);
            buffer.clear();

            res = channel.read(buffer);
            if (res == 0) {
                return new UndertowRequestHttpBody(context, exchange, firstData);
            }
            if (res < 0) {
                var contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
                return HttpBody.of(context, contentType, firstData);
            }
            buffer.flip();
            var secondData = new byte[buffer.remaining()];
            buffer.get(secondData);
            return new UndertowRequestHttpBody(context, exchange, firstData, secondData);
        }
    }
}

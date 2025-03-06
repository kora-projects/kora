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
import java.util.*;
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
        if(undertowQueryParams.isEmpty()) {
            return Map.of();
        }

        var queryParams = new LinkedHashMap<String, List<String>>(undertowQueryParams.size());
        for (var entry : undertowQueryParams.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue().stream()
                .filter(Predicate.not(String::isEmpty))
                .toList();
            queryParams.put(key, value);
        }
        return Collections.unmodifiableMap(queryParams);
    }

    private HttpBodyInput getContent(HttpServerExchange exchange) throws IOException {
        if (exchange.isRequestComplete()) {
            // request body is empty
            return HttpBody.empty();
        }
        if (!exchange.isInIoThread()) {
            return new UndertowRequestHttpBody(context, exchange, null);
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
                return new UndertowRequestHttpBody(context, exchange, null);
            }
            // fast path for single buffer requests
            buffer.flip();
            var data = new byte[res];
            buffer.get(data);
            buffer.clear();

            res = channel.read(buffer);
            if (res == -1) {
                var contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
                return HttpBody.of(context, contentType, data);
            }
            // slower path for multiple buffer requests that has not arrived yet
            if (res == 0) {
                var prefetched = new LinkedList<byte[]>();
                prefetched.add(data);
                return new UndertowRequestHttpBody(context, exchange, prefetched);
            }

            // slow path with linked list for multiple buffer requests
            var prefetched = new LinkedList<byte[]>();
            prefetched.add(data);
            var len = data.length;

            while (res > 0) {
                buffer.flip();
                data = new byte[res];
                len += res;
                buffer.get(data);
                buffer.clear();
                prefetched.add(data);
                res = channel.read(buffer);
            }

            if (res < 0) {
                var contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
                data = new byte[len];
                var pos = 0;

                for (var bytes : prefetched) {
                    System.arraycopy(bytes, 0, data, pos, bytes.length);
                    pos += bytes.length;
                }

                return HttpBody.of(context, contentType, data);
            }

            return new UndertowRequestHttpBody(context, exchange, prefetched);
        }
    }
}

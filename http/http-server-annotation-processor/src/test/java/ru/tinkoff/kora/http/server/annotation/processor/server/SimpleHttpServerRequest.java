package ru.tinkoff.kora.http.server.annotation.processor.server;

import reactor.core.publisher.Flux;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SimpleHttpServerRequest implements HttpServerRequest {
    private final String method;
    private final String path;
    private final byte[] body;
    private final Map.Entry<String, String>[] headers;
    private final Map<String, String> routeParams;

    public SimpleHttpServerRequest(String method, String path, byte[] body, Map.Entry<String, String>[] headers, Map<String, String> routeParams) {
        this.method = method;
        this.path = path;
        this.body = body;
        this.headers = headers;
        this.routeParams = routeParams;
    }

    @Override
    public String method() {
        return method;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public HttpHeaders headers() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map.Entry<String, List<String>>[] entries = new Map.Entry[headers.length];
        for (int i = 0; i < headers.length; i++) {
            entries[i] = Map.entry(headers[i].getKey(), List.of(headers[i].getValue()));
        }
        return HttpHeaders.of(entries);
    }

    @Override
    public Map<String, Deque<String>> queryParams() {
        var questionMark = path.indexOf('?');
        if (questionMark < 0) {
            return Map.of();
        }
        var params = path.substring(questionMark + 1);
        return Stream.of(params.split("&"))
            .map(param -> {
                var eq = param.indexOf('=');
                if (eq <= 0) {
                    return Map.entry(param, new ArrayDeque<String>(0));
                }
                var name = param.substring(0, eq);
                var value = param.substring(eq + 1);
                return Map.entry(name, new ArrayDeque<>(List.of(value)));
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (d1, d2) -> {
                var d3 = new ArrayDeque<>(d1);
                d3.addAll(d2);
                return d3;
            }));
    }

    @Override
    public Map<String, String> pathParams() {
        return routeParams;
    }

    @Override
    public Flux<ByteBuffer> body() {
        return Flux.just(ByteBuffer.wrap(body));
    }
}

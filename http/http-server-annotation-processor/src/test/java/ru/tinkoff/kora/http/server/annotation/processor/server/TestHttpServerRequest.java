package ru.tinkoff.kora.http.server.annotation.processor.server;

import ru.tinkoff.kora.http.common.MutableHttpHeaders;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.cookie.Cookies;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestHttpServerRequest implements HttpServerRequest {
    private final String method;
    private final String route;
    private final String path;
    private final byte[] body;
    private final MutableHttpHeaders headers;
    private final Map<String, String> routeParams;

    public TestHttpServerRequest(String method, String route, String path, byte[] body, MutableHttpHeaders headers, Map<String, String> routeParams) {
        this.method = method;
        this.route = route;
        this.path = path;
        this.body = body;
        this.headers = headers;
        this.routeParams = routeParams;
    }

    public static TestHttpServerRequest of(String method, String path, String body, HttpHeaders headers) {
        return new TestHttpServerRequest(method, path, path, body.getBytes(StandardCharsets.UTF_8), headers.toMutable(), new HashMap<>());
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
    public String route() {
        return this.route;
    }

    @Override
    public MutableHttpHeaders headers() {
        return this.headers;
    }

    @Override
    public List<Cookie> cookies() {
        var cookies = headers.getAll("cookies");
        var list = new ArrayList<Cookie>();
        Cookies.parseRequestCookies(500, true, cookies, list);
        return list;
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
    public HttpBodyInput body() {
        return HttpBody.of(headers().getFirst("content-type"), ByteBuffer.wrap(body));
    }
}

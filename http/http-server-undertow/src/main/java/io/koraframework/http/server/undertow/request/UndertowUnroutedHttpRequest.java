package io.koraframework.http.server.undertow.request;

import io.undertow.server.HttpServerExchange;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.router.UnroutedHttpRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

public final class UndertowUnroutedHttpRequest implements UnroutedHttpRequest {

    private final HttpServerExchange exchange;
    private final String method;
    private final String path;
    private final UndertowHttpHeaders headers;

    private volatile HttpBodyInput body;

    public UndertowUnroutedHttpRequest(HttpServerExchange exchange) {
        this.exchange = exchange;
        this.method = exchange.getRequestMethod().toString();
        this.path = reencodePercent(exchange.getRelativePath(), exchange.getRequestURI());
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
    public String host() {
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
    public Map<String, List<String>> queryParams() {
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
            throw new UncheckedIOException("HTTP request body cannot be opened for %s %s; check client connection and server I/O cause: %s".formatted(this.method, this.path, e.getMessage()), e);
        }
        return this.body = b;
    }

    @Override
    public long requestStartTimeInNanos() {
        return exchange.getRequestStartTime();
    }

    /**
     * Undertow decodes the path but leaves {@code %2F}/{@code %2f} and {@code %5C}/{@code %5c} encoded,
     * while {@code %25} becomes a plain {@code '%'}.
     * After that an encoded slash and an escaped literal {@code "%2f"} ({@code %252f} on the wire) look the same.
     * Here every {@code '%'} that came from {@code %25} is written back as {@code "%25"}, so the path contains only
     * {@code %2F}/{@code %2f}, {@code %5C}/{@code %5c} and {@code %25} escapes and path parameters can be decoded exactly once
     * (see {@code HttpRequestHandlerUtils.parsePathString}).
     */
    static String reencodePercent(String decodedPath, String rawUri) {
        if (decodedPath.indexOf('%') == -1) {
            return decodedPath;
        }

        // for every %25, %2F, %2f, %5C and %5c escape of the raw path: true if it is %25
        var escapes = new ArrayList<Boolean>();
        for (int i = 0; i < rawUri.length(); i++) {
            var c = rawUri.charAt(i);
            if (c == '?') {
                break;
            } else if (c == ';') {
                // path parameters (";a=b") are removed from the decoded path by Undertow
                var next = rawUri.indexOf('/', i);
                if (next == -1) {
                    break;
                }
                i = next;
            } else if (c == '%' && i + 2 < rawUri.length()) {
                var high = rawUri.charAt(i + 1);
                var low = rawUri.charAt(i + 2);
                if (high == '2' && low == '5') {
                    escapes.add(true);
                } else if (high == '2' && (low == 'F' || low == 'f') || high == '5' && (low == 'C' || low == 'c')) {
                    escapes.add(false);
                }
                i += 2;
            }
        }

        var percents = 0;
        for (int i = 0; i < decodedPath.length(); i++) {
            if (decodedPath.charAt(i) == '%') {
                percents++;
            }
        }
        if (percents > escapes.size()) {
            return decodedPath;
        }

        // the relative path is the tail of the raw path, so its '%' match the last escapes
        var escapeIndex = escapes.size() - percents;
        var builder = new StringBuilder(decodedPath.length() + 2 * percents);
        for (int i = 0; i < decodedPath.length(); i++) {
            var c = decodedPath.charAt(i);
            builder.append(c);
            if (c == '%' && escapes.get(escapeIndex++)) {
                builder.append("25");
            }
        }
        return builder.toString();
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

    @Override
    public String toString() {
        return exchange.toString();
    }
}

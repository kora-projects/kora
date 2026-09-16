package io.koraframework.http.server.undertow.handler;

import io.koraframework.http.server.common.HttpServerConfig;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jspecify.annotations.Nullable;

public final class KoraCorsHttpHandler implements HttpHandler {

    public static final HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
    public static final HttpString ACCESS_CONTROL_ALLOW_CREDENTIALS = HttpString.tryFromString("Access-Control-Allow-Credentials");
    public static final HttpString ACCESS_CONTROL_ALLOW_HEADERS = HttpString.tryFromString("Access-Control-Allow-Headers");
    public static final HttpString ACCESS_CONTROL_ALLOW_METHODS = HttpString.tryFromString("Access-Control-Allow-Methods");
    public static final HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
    public static final HttpString ACCESS_CONTROL_MAX_AGE = HttpString.tryFromString("Access-Control-Max-Age");
    public static final HttpString ACCESS_CONTROL_REQUEST_METHOD = HttpString.tryFromString("Access-Control-Request-Method");

    private final HttpHandler next;
    private final HttpServerConfig.HttpServerCorsConfig config;
    private final String allowMethods;
    private final String allowHeaders;
    private final String allowCredentials;
    @Nullable
    private final String exposeHeaders;
    private final String maxAge;

    public KoraCorsHttpHandler(HttpHandler next, HttpServerConfig.HttpServerCorsConfig config) {
        this.next = next;
        this.config = config;
        this.allowMethods = String.join(",", config.allowMethods());
        this.allowHeaders = String.join(",", config.allowHeaders());
        this.allowCredentials = String.valueOf(config.allowCredentials());
        this.exposeHeaders = config.exposeHeaders().isEmpty()
            ? null
            : String.join(",", config.exposeHeaders());
        this.maxAge = String.valueOf(config.maxAge().getSeconds());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var origin = exchange.getRequestHeaders().getFirst(Headers.ORIGIN);
        if (origin == null) {
            this.next.handleRequest(exchange);
            return;
        }

        this.applyCorsPolicy(exchange, origin);
        if (isPreflight(exchange)) {
            exchange.setStatusCode(204);
            exchange.endExchange();
            return;
        }

        this.next.handleRequest(exchange);
    }

    private void applyCorsPolicy(HttpServerExchange exchange, String origin) {
        var allowOrigin = this.config.allowOrigin();
        if (allowOrigin == null) {
            allowOrigin = origin;
            this.addVaryOrigin(exchange);
        }

        this.putIfAbsent(exchange, ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
        this.putIfAbsent(exchange, ACCESS_CONTROL_ALLOW_HEADERS, this.allowHeaders);
        this.putIfAbsent(exchange, ACCESS_CONTROL_ALLOW_CREDENTIALS, this.allowCredentials);
        this.putIfAbsent(exchange, ACCESS_CONTROL_ALLOW_METHODS, this.allowMethods);
        this.putIfAbsent(exchange, ACCESS_CONTROL_MAX_AGE, this.maxAge);
        if (this.exposeHeaders != null) {
            this.putIfAbsent(exchange, ACCESS_CONTROL_EXPOSE_HEADERS, this.exposeHeaders);
        }
    }

    private static boolean isPreflight(HttpServerExchange exchange) {
        return exchange.getRequestMethod().equals(Methods.OPTIONS)
            && exchange.getRequestHeaders().contains(ACCESS_CONTROL_REQUEST_METHOD);
    }

    private void putIfAbsent(HttpServerExchange exchange, HttpString name, String value) {
        if (!exchange.getResponseHeaders().contains(name)) {
            exchange.getResponseHeaders().put(name, value);
        }
    }

    private void addVaryOrigin(HttpServerExchange exchange) {
        var vary = exchange.getResponseHeaders().get(Headers.VARY);
        if (vary == null || !vary.contains(Headers.ORIGIN_STRING)) {
            exchange.getResponseHeaders().add(Headers.VARY, Headers.ORIGIN_STRING);
        }
    }
}

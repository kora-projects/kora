package ru.tinkoff.kora.bpmn.camunda7.rest;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.router.PathTemplateMatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CamundaRestHttpHandler implements HttpHandler {

    private final Map<String, PathTemplateMatcher<HttpServerRequestHandler>> pathTemplateMatcher = new HashMap<>();
    private final PathTemplateMatcher<List<String>> allMethodMatchers = new PathTemplateMatcher<>();

    private final HttpHandler camundaHttpHandler;
    private final HttpHandler publicHttpHandler;

    public CamundaRestHttpHandler(HttpHandler restHttpHandler,
                                  HttpHandler camundaHttpHandler,
                                  List<HttpServerRequestHandler> handlers,
                                  HttpServerConfig config) {
        this.camundaHttpHandler = restHttpHandler;
        this.publicHttpHandler = camundaHttpHandler;

        for (var h : handlers) {
            var route = h.routeTemplate();
            var methodMatchers = pathTemplateMatcher.computeIfAbsent(h.method(), k -> new PathTemplateMatcher<>());
            var oldValue = methodMatchers.add(route, h);
            if (oldValue == null) {
                if (config.ignoreTrailingSlash()) {
                    if (!route.endsWith("*")) {
                        if (route.charAt(route.length() - 1) == '/') {
                            route = route.substring(0, route.length() - 1);
                        } else {
                            route = route + '/';
                        }
                        oldValue = methodMatchers.add(route, h);
                        if (oldValue != null) {
                            continue;
                        }
                    }
                }
                var otherMethods = new ArrayList<>(List.of(h.method()));
                var oldAllMethodValue = allMethodMatchers.add(route, otherMethods);
                if (oldAllMethodValue != null) {
                    otherMethods.addAll(oldAllMethodValue.getValue());
                }
            }
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String method = exchange.getRequestMethod().toString();
        String path = exchange.getResolvedPath();

        var methodMatchers = pathTemplateMatcher.get(method);
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(path);
        if (pathTemplateMatch == null) {
            var allMethodMatch = allMethodMatchers.match(path);
            if (allMethodMatch != null) {
                publicHttpHandler.handleRequest(exchange);
            } else {
                camundaHttpHandler.handleRequest(exchange);
            }
        } else {
            publicHttpHandler.handleRequest(exchange);
        }
    }
}

package ru.tinkoff.kora.http.server.common.router;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class that provides fast path matching of path templates. Templates are stored in a map based on the stem of the template,
 * and matches longest stem first.
 * <p>
 * TODO: we can probably do this faster using a trie type structure, but I think the current impl should perform ok most of the time
 *
 * @author Stuart Douglas
 */

public class PublicApiHandler {

    private static final HttpServerResponse NOT_FOUND_RESPONSE = HttpServerResponse.of(404);
    private static final HttpServerRequestHandler.HandlerFunction NOT_FOUND_HANDLER = (ctx, request) -> NOT_FOUND_RESPONSE;

    private final Map<String, PathTemplateMatcher<HttpServerRequestHandler>> pathTemplateMatcher;
    private final PathTemplateMatcher<List<String>> allMethodMatchers;
    private final AtomicReference<RequestHandler> requestHandler = new AtomicReference<>();

    public PublicApiHandler(List<HttpServerRequestHandler> handlers, List<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        this.pathTemplateMatcher = new HashMap<>();
        this.allMethodMatchers = new PathTemplateMatcher<>();
        for (var h : handlers) {
            if (!h.enabled()) {
                continue;
            }

            var route = h.routeTemplate();
            var methodMatchers = this.pathTemplateMatcher.computeIfAbsent(h.method(), k -> new PathTemplateMatcher<>());
            var oldValue = methodMatchers.add(route, h);
            if (oldValue != null) {
                throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
            }
            if (config.ignoreTrailingSlash()) {
                if (!route.endsWith("*")) {
                    if (route.charAt(route.length() - 1) == '/') {
                        route = route.substring(0, route.length() - 1);
                    } else {
                        route = route + '/';
                    }
                    oldValue = methodMatchers.add(route, h);
                    if (oldValue != null) {
                        throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
                    }
                }
            }
            var otherMethods = new ArrayList<>(List.of(h.method()));
            var oldAllMethodValue = this.allMethodMatchers.add(route, otherMethods);
            if (oldAllMethodValue != null) {
                otherMethods.addAll(oldAllMethodValue.getValue());
            }
        }
        if (interceptors.isEmpty()) {
            this.requestHandler.set(new SimpleRequestHandler());
        } else {
            this.requestHandler.set(new AggregatedRequestHandler(interceptors));
        }
    }

    public static class PublicApiInvocation {
        private final RequestHandler handler;
        private final HttpServerRequestHandler.HandlerFunction handlerFunction;
        public final HttpServerRequest request;

        private PublicApiInvocation(RequestHandler handler, HttpServerRequestHandler.HandlerFunction handlerFunction, HttpServerRequest request) {
            this.handler = handler;
            this.handlerFunction = handlerFunction;
            this.request = request;
        }

        public HttpServerResponse proceed(HttpServerRequest request) throws Exception {
            return this.handler.apply(Context.current(), request, this.handlerFunction);
        }
    }

    public PublicApiInvocation route(PublicApiRequest publicApiRequest) {
        final HttpServerRequestHandler.HandlerFunction handlerFunction;
        final Map<String, String> templateParameters;
        final @Nullable String routeTemplate;

        var methodMatchers = this.pathTemplateMatcher.get(publicApiRequest.method());
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(publicApiRequest.path());
        if (pathTemplateMatch == null) {
            var allMethodMatch = this.allMethodMatchers.match(publicApiRequest.path());
            if (allMethodMatch != null) {
                var allowed = String.join(", ", allMethodMatch.value());
                handlerFunction = (_, _) -> {
                    throw HttpServerResponseException.of(405, "Method Not Allowed", HttpHeaders.of("allow", allowed));
                };
                routeTemplate = allMethodMatch.matchedTemplate();
                templateParameters = Map.of();
            } else {
                handlerFunction = NOT_FOUND_HANDLER;
                routeTemplate = null;
                templateParameters = Map.of();
            }
        } else {
            templateParameters = pathTemplateMatch.parameters();
            routeTemplate = pathTemplateMatch.matchedTemplate();
            handlerFunction = pathTemplateMatch.value()::handle;
        }
        var request = new LazyRequest(publicApiRequest, templateParameters, routeTemplate);
        var handler = this.requestHandler.get();
        return new PublicApiInvocation(handler, handlerFunction, request);
    }


    private interface RequestHandler {
        HttpServerResponse apply(Context context, HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception;
    }

    private static class SimpleRequestHandler implements RequestHandler {
        @Override
        public HttpServerResponse apply(Context context, HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
            return lastHandlerInChain.apply(context, request);
        }
    }

    private static class AggregatedRequestHandler implements RequestHandler {
        private static final RequestHandler FINAL_HANDLER = (ctx, request, lastHandlerInChain) -> lastHandlerInChain.apply(ctx, request);
        private final RequestHandler chain;

        private AggregatedRequestHandler(List<HttpServerInterceptor> interceptors) {
            var chain = FINAL_HANDLER;
            for (var interceptor : interceptors) {
                var remainingChain = chain;
                chain = (ctx, r, lastHandler) -> {
                    var oldCtx = Context.current();
                    try {
                        ctx.inject();
                        return interceptor.intercept(ctx, r, (_ctx, httpServerRequest) -> remainingChain.apply(_ctx, httpServerRequest, lastHandler));
                    } finally {
                        oldCtx.inject();
                    }
                };
            }
            this.chain = chain;
        }

        @Override
        public HttpServerResponse apply(Context ctx, HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
            return this.chain.apply(ctx, request, lastHandlerInChain);
        }
    }
}

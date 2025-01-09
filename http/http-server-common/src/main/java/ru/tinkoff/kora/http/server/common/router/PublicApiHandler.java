package ru.tinkoff.kora.http.server.common.router;


import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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

    private static final CompletionStage<HttpServerResponse> NOT_FOUND_RESPONSE = CompletableFuture.completedFuture(
        HttpServerResponse.of(404)
    );
    private static final HttpServerRequestHandler.HandlerFunction NOT_FOUND_HANDLER = (ctx, request) -> NOT_FOUND_RESPONSE;

    private final Map<String, PathTemplateMatcher<HttpServerRequestHandler>> pathTemplateMatcher;
    private final PathTemplateMatcher<List<String>> allMethodMatchers;
    private final AtomicReference<RequestHandler> requestHandler = new AtomicReference<>();
    private final HttpServerTelemetry telemetry;

    public PublicApiHandler(List<HttpServerRequestHandler> handlers, List<HttpServerInterceptor> interceptors, HttpServerTelemetryFactory httpServerTelemetry, HttpServerConfig config) {
        this.telemetry = Objects.requireNonNullElse(httpServerTelemetry.get(config.telemetry()), HttpServerTelemetry.EMPTY);
        this.pathTemplateMatcher = new HashMap<>();
        this.allMethodMatchers = new PathTemplateMatcher<>();
        for (var h : handlers) {
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

    public PublicApiResponse process(Context context, PublicApiRequest publicApiRequest) {
        final HttpServerRequestHandler.HandlerFunction handlerFunction;
        final Map<String, String> templateParameters;
        final @Nullable String routeTemplate;

        var methodMatchers = this.pathTemplateMatcher.get(publicApiRequest.method());
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(publicApiRequest.path());
        if (pathTemplateMatch == null) {
            var allMethodMatch = this.allMethodMatchers.match(publicApiRequest.path());
            if (allMethodMatch != null) {
                var allowed = String.join(", ", allMethodMatch.value());
                handlerFunction = (ctx, request) -> CompletableFuture.failedFuture(HttpServerResponseException.of(405, "Method Not Allowed", HttpHeaders.of("allow", allowed)));
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
        var tctx = this.telemetry.get(publicApiRequest, routeTemplate);

        try {
            var future = this.requestHandler.get().apply(context, request, handlerFunction);
            return new PublicApiResponseImpl(tctx, future.toCompletableFuture());
        } catch (CompletionException error) {
            return new PublicApiResponseImpl(tctx, CompletableFuture.failedFuture(Objects.requireNonNullElse(error.getCause(), error)));
        } catch (Throwable error) {
            return new PublicApiResponseImpl(tctx, CompletableFuture.failedFuture(error));
        }
    }


    private interface RequestHandler {
        CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception;
    }

    private static class SimpleRequestHandler implements RequestHandler {
        @Override
        public CompletionStage<HttpServerResponse> apply(Context context, HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
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
        public CompletionStage<HttpServerResponse> apply(Context ctx, HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
            return this.chain.apply(ctx, request, lastHandlerInChain);
        }
    }
}

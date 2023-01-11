package ru.tinkoff.kora.http.server.common.router;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.RefreshListener;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.*;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerLogger;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Utility class that provides fast path matching of path templates. Templates are stored in a map based on the stem of the template,
 * and matches longest stem first.
 * <p>
 * TODO: we can probably do this faster using a trie type structure, but I think the current impl should perform ok most of the time
 *
 * @author Stuart Douglas
 */

public class PublicApiHandler implements RefreshListener {
    private final Function<HttpServerRequest, Mono<HttpServerResponse>> NOT_FOUND_HANDLER = request ->
        Mono.just(new SimpleHttpServerResponse(404, "application/octet-stream", HttpHeaders.of(), null));

    private final Map<String, PathTemplateMatcher<ValueOf<HttpServerRequestHandler>>> pathTemplateMatcher;
    private final PathTemplateMatcher<List<String>> allMethodMatchers;
    private final All<ValueOf<HttpServerRequestHandler>> handlers;
    private final All<ValueOf<HttpServerInterceptor>> interceptors;
    private final AtomicReference<RequestHandler> requestHandler = new AtomicReference<>();
    private final ValueOf<HttpServerTelemetry> telemetry;

    public PublicApiHandler(All<ValueOf<HttpServerRequestHandler>> handlers, All<ValueOf<HttpServerInterceptor>> interceptors, ValueOf<HttpServerTelemetry> httpServerTelemetry) {
        this.handlers = handlers;
        this.interceptors = interceptors;
        this.telemetry = httpServerTelemetry;
        this.pathTemplateMatcher = new HashMap<>();
        this.allMethodMatchers = new PathTemplateMatcher<>();
        for (var h : handlers) {
            var handler = h.get();
            var route = handler.routeTemplate();
            var methodMatchers = this.pathTemplateMatcher.computeIfAbsent(handler.method(), k -> new PathTemplateMatcher<>());
            var oldValue = methodMatchers.add(route, h);
            if (oldValue != null) {
                throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
            }
            var otherMethods = new ArrayList<>(List.of(handler.method()));
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

    @Override
    public void graphRefreshed() {
        if (interceptors.isEmpty()) {
            requestHandler.set(new SimpleRequestHandler());
        } else {
            requestHandler.set(new AggregatedRequestHandler(interceptors));
        }
    }

    public int handlersSize() {
        return this.handlers.size();
    }

    public record PublicApiRequest(String method, String path, String hostName, String scheme, HttpHeaders headers, Map<String, ? extends Collection<String>> queryParams, Flux<ByteBuffer> body) {}

    public void process(PublicApiRequest routerRequest, HttpServerResponseSender responseSender) {
        final Function<HttpServerRequest, Mono<HttpServerResponse>> handlerFunction;
        final Map<String, String> templateParameters;
        final @Nullable String routeTemplate;

        var methodMatchers = pathTemplateMatcher.get(routerRequest.method());
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(routerRequest.path());
        if (pathTemplateMatch == null) {
            var allMethodMatch = allMethodMatchers.match(routerRequest.path);
            if (allMethodMatch != null) {
                var allowed = String.join(", ", allMethodMatch.value());
                handlerFunction = request -> Mono.just(new SimpleHttpServerResponse(405, "application/octet-stream", HttpHeaders.of("allow", allowed), null));
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
            handlerFunction = pathTemplateMatch.value().get()::handle;
        }

        var request = new Request(routerRequest.method(), routerRequest.path(), routeTemplate, routerRequest.headers(), routerRequest.queryParams(), templateParameters, routerRequest.body());

        var ctx = this.telemetry.get().get(routerRequest, routeTemplate);
        var method = routerRequest.method;

        try {
            this.requestHandler.get().apply(request, handlerFunction)
                .switchIfEmpty(Mono.error(() -> new Exception(String.format("Empty result stream for `%1$s` request handler. Possibly request controller returns `null` as result", operation(method, routeTemplate)))))
                .subscribe(
                    response -> this.sendResponse(ctx, responseSender, response, null),
                    error -> {
                        var response = error instanceof HttpServerResponse httpServerResponse
                            ? httpServerResponse
                            : new SimpleHttpServerResponse(500, "text/plain", HttpHeaders.of(), StandardCharsets.UTF_8.encode(
                            Objects.requireNonNullElse(error.getMessage(), "Unknown error")
                        ));
                        this.sendResponse(ctx, responseSender, response, error);
                    });

        } catch (Throwable error) {
            var response = error instanceof HttpServerResponse httpServerResponse
                ? httpServerResponse
                : new SimpleHttpServerResponse(500, "text/plain", HttpHeaders.of(), StandardCharsets.UTF_8.encode(
                Objects.requireNonNullElse(error.getMessage(), "Unknown error")
            ));
            this.sendResponse(ctx, responseSender, response, error);
        }
    }

    private void sendResponse(HttpServerTelemetry.HttpServerTelemetryContext ctx, HttpServerResponseSender responseSender, HttpServerResponse response, @Nullable Throwable exception) {
        responseSender.send(response).subscribe(result -> {
            if (result instanceof HttpServerResponseSender.Success success) {
                var resultCode = HttpResultCode.fromStatusCode(success.code());
                ctx.close(success.code(), resultCode, exception);
            } else if (result instanceof HttpServerResponseSender.ResponseBodyErrorBeforeCommit responseBodyError) {
                var newResponse = new SimpleHttpServerResponse(500, "text/plain", HttpHeaders.of(), StandardCharsets.UTF_8.encode(
                    Objects.requireNonNullElse(responseBodyError.error().getMessage(), "Unknown error")
                ));
                responseSender.send(newResponse).subscribe(v -> {
                    ctx.close(500, HttpResultCode.SERVER_ERROR, responseBodyError.error());
                });
            } else if (result instanceof HttpServerResponseSender.ResponseBodyError responseBodyError) {
                ctx.close(response.code(), HttpResultCode.SERVER_ERROR, responseBodyError.error());
            } else if (result instanceof HttpServerResponseSender.ConnectionError connectionError) {
                ctx.close(response.code(), HttpResultCode.CONNECTION_ERROR, connectionError.error());
            }
        }, e -> {
            HttpServerLogger.log.error("Error dropped: looks like a bug in HttpServerResponseSender", e);
        });
    }

    private String operation(String method, String routeTemplate) {
        return method + " " + routeTemplate;
    }

    private record Request(
        String method,
        String path,
        @Nullable String matchedTemplate,
        HttpHeaders headers,
        Map<String, ? extends Collection<String>> queryParams,
        Map<String, String> pathParams,
        Flux<ByteBuffer> body)

        implements HttpServerRequest {
    }

    private interface RequestHandler extends BiFunction<HttpServerRequest, Function<HttpServerRequest, Mono<HttpServerResponse>>, Mono<HttpServerResponse>> {}

    private static class SimpleRequestHandler implements RequestHandler {
        @Override
        public Mono<HttpServerResponse> apply(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
            return handler.apply(request);
        }
    }

    private static class AggregatedRequestHandler implements RequestHandler {

        private static final Object HANDLER_KEY = new Object();

        @SuppressWarnings("unchecked")
        private static final Function<HttpServerRequest, Mono<HttpServerResponse>> CONTEXTUAL_HANDLER = request ->
            Mono.deferContextual(context -> ((Function<HttpServerRequest, Mono<HttpServerResponse>>) context.get(HANDLER_KEY)).apply(request));

        private final Function<HttpServerRequest, Mono<HttpServerResponse>> chain;

        private AggregatedRequestHandler(All<ValueOf<HttpServerInterceptor>> interceptors) {
            Function<HttpServerRequest, Mono<HttpServerResponse>> chain = CONTEXTUAL_HANDLER;
            for (var httpServerInterceptorValueOf : interceptors) {
                var interceptor = httpServerInterceptorValueOf.get();
                var previousChain = chain;
                chain = r -> interceptor.intercept(r, previousChain);
            }

            this.chain = chain;
        }

        @Override
        public Mono<HttpServerResponse> apply(HttpServerRequest request, Function<HttpServerRequest, Mono<HttpServerResponse>> handler) {
            return chain.apply(request).contextWrite(ctx -> ctx.put(HANDLER_KEY, handler));
        }
    }
}

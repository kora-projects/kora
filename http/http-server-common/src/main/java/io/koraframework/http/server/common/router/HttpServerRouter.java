package io.koraframework.http.server.common.router;


import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.interceptor.HttpServerInterceptor;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.request.RoutedHttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Resolves incoming HTTP requests to registered request handlers.
 *
 * <p>The router is assembled once during construction. Enabled handlers are grouped by HTTP
 * method and their route templates. A separate method-independent matcher records the methods associated with each
 * route and is used to distinguish an unknown path ({@code 404 Not Found}) from a known path
 * requested with an unsupported method ({@code 405 Method Not Allowed}). Consequently, routing
 * performs no matcher mutation, rebuilding, synchronization, or volatile state access.</p>
 *
 * <p>For a request, routing proceeds as follows:</p>
 * <ol>
 *     <li>select the matcher associated with the request method;</li>
 *     <li>match the request path using exact, parameter, and terminal-wildcard route semantics;</li>
 *     <li>if no method-specific route matches, consult the method-independent matcher and select
 *     either the {@code 405} handler or the {@code 404} handler;</li>
 *     <li>wrap the original request in a
 *     {@link io.koraframework.http.server.common.request.RoutedHttpServerRequest} containing the
 *     matched template and captured parameters;</li>
 *     <li>return an invocation that executes the configured interceptor chain followed by the
 *     selected request handler.</li>
 * </ol>
 *
 * <p>When {@link HttpServerConfig#ignoreTrailingSlash()} is enabled, an additional route variant
 * with the opposite trailing-slash form is registered, except for terminal-wildcard templates.
 * Equivalent templates for the same HTTP method are rejected during construction. Interceptors
 * are ordered deterministically by their class simple name and the resulting chain is also built
 * once.</p>
 *
 * <p>Instances are immutable after construction and may be shared by concurrent request-processing
 * threads.</p>
 */
public class HttpServerRouter {

    private static final HttpServerResponse NOT_FOUND_RESPONSE = HttpServerResponse.of(404);
    private static final HttpServerRequestHandler.HandlerFunction NOT_FOUND_HANDLER = (_) -> NOT_FOUND_RESPONSE;

    private final Map<String, HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction>> pathTemplateMatcher;
    private final HybridPathTemplateMatcher<MethodNotAllowedHandler> allMethodMatchers;
    private final RequestHandler requestHandler;

    /**
     * Builds a router from the supplied handlers and interceptors.
     *
     * <p>Disabled handlers are ignored. All route indexes and the interceptor chain are fully
     * compiled before this constructor returns; later changes to the supplied iterables do not
     * affect the router.</p>
     *
     * @param handlers registered HTTP request handlers
     * @param interceptors interceptors applied to every routed invocation
     * @param config server configuration controlling routing options such as trailing-slash handling
     * @throws IllegalStateException if the same HTTP method contains equivalent route templates
     * @throws IllegalArgumentException if a route template is invalid
     */
    public HttpServerRouter(Iterable<HttpServerRequestHandler> handlers, Iterable<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        var matcherBuilders = new HashMap<String, HybridPathTemplateMatcher.Builder<HttpServerRequestHandler.HandlerFunction>>();
        var allMethodMatcherBuilder = HybridPathTemplateMatcher.<MethodNotAllowedHandler>builder();
        for (var h : handlers) {
            if (!h.enabled()) {
                continue;
            }

            var route = h.routeTemplate();
            var handlerFunction = new RouteHandlerFunction(h);
            var methodMatcherBuilder = matcherBuilders.computeIfAbsent(h.method(), _ -> HybridPathTemplateMatcher.builder());
            var oldValue = methodMatcherBuilder.add(route, handlerFunction);
            if (oldValue != null) {
                throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
            }
            if (config.ignoreTrailingSlash()) {
                var lastRouteChar = route.charAt(route.length() - 1);
                if (lastRouteChar != '*') {
                    if (lastRouteChar == '/') {
                        route = route.substring(0, route.length() - 1);
                    } else {
                        route = route + '/';
                    }
                    oldValue = methodMatcherBuilder.add(route, handlerFunction);
                    if (oldValue != null) {
                        throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
                    }
                }
            }
            var otherMethods = new MethodNotAllowedHandler(h.method());
            var oldAllMethodValue = allMethodMatcherBuilder.add(route, otherMethods);
            if (oldAllMethodValue != null) {
                oldAllMethodValue.getValue().add(h.method());
            }
        }
        var pathTemplateMatchers = new HashMap<String, HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction>>(matcherBuilders.size());
        for (var entry : matcherBuilders.entrySet()) {
            pathTemplateMatchers.put(entry.getKey(), entry.getValue().build());
        }
        this.pathTemplateMatcher = Map.copyOf(pathTemplateMatchers);
        this.allMethodMatchers = allMethodMatcherBuilder.build();

        var interceptorsList = new ArrayList<HttpServerInterceptor>();
        for (var interceptor : interceptors) {
            interceptorsList.add(interceptor);
        }
        interceptorsList.sort(Comparator.comparing(i -> i.getClass().getSimpleName()));

        if (interceptorsList.isEmpty()) {
            this.requestHandler = SimpleRequestHandler.INSTANCE;
        } else {
            this.requestHandler = new AggregatedRequestHandler(interceptorsList);
        }
    }

    /**
     * Resolves an unrouted request and creates its executable invocation.
     *
     * <p>The returned invocation always contains a
     * {@link io.koraframework.http.server.common.request.RoutedHttpServerRequest}. For a matched
     * route it exposes the matched template and captured parameters. For an unmatched route the
     * template is {@code null} and the parameter map is empty. A method mismatch is represented by
     * an invocation whose handler produces {@code 405 Method Not Allowed} with an {@code Allow}
     * header; a path mismatch produces {@code 404 Not Found}.</p>
     *
     * <p>This method only performs lookups and request wrapping. The selected handler and
     * interceptors run when {@link HttpServerInvocation#proceed(HttpServerRequest)} is called.</p>
     *
     * @param unroutedHttpRequest request containing the method and raw path to resolve
     * @return invocation containing the routed request and the selected execution chain
     */
    public HttpServerInvocation route(UnroutedHttpRequest unroutedHttpRequest) {
        final HttpServerRequestHandler.HandlerFunction handlerFunction;
        final Map<String, String> templateParameters;
        final @Nullable String pathTemplate;

        var methodMatchers = this.pathTemplateMatcher.get(unroutedHttpRequest.method());
        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(unroutedHttpRequest.path());
        if (pathTemplateMatch == null) {
            var allMethodMatch = this.allMethodMatchers.match(unroutedHttpRequest.path());
            if (allMethodMatch != null) {
                handlerFunction = allMethodMatch.value();
                pathTemplate = allMethodMatch.matchedTemplate();
            } else {
                handlerFunction = NOT_FOUND_HANDLER;
                pathTemplate = null;
            }
            templateParameters = Map.of();
        } else {
            templateParameters = pathTemplateMatch.parameters();
            pathTemplate = pathTemplateMatch.matchedTemplate();
            handlerFunction = pathTemplateMatch.value();
        }
        var request = new RoutedHttpServerRequest(unroutedHttpRequest, templateParameters, pathTemplate);
        return new PublicApiInvocation(this.requestHandler, handlerFunction, request);
    }

    private record PublicApiInvocation(RequestHandler handler,
                                       HttpServerRequestHandler.HandlerFunction handlerFunction,
                                       HttpServerRequest routedRequest) implements HttpServerInvocation {

        public HttpServerResponse proceed(HttpServerRequest request) throws Exception {
            return this.handler.apply(request, this.handlerFunction);
        }
    }

    private interface RequestHandler {
        HttpServerResponse apply(HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception;
    }

    private static class SimpleRequestHandler implements RequestHandler {
        private static final SimpleRequestHandler INSTANCE = new SimpleRequestHandler();

        @Override
        public HttpServerResponse apply(HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
            return lastHandlerInChain.apply(request);
        }
    }

    private record RouteHandlerFunction(HttpServerRequestHandler handler) implements HttpServerRequestHandler.HandlerFunction {
        @Override
        public HttpServerResponse apply(HttpServerRequest request) throws Exception {
            return this.handler.handle(request);
        }
    }

    private static final class MethodNotAllowedHandler implements HttpServerRequestHandler.HandlerFunction {
        private final List<String> methods = new ArrayList<>(4);
        private String allowed;

        private MethodNotAllowedHandler(String method) {
            this.add(method);
        }

        private void add(String method) {
            this.methods.add(method);
            this.allowed = String.join(", ", this.methods);
        }

        @Override
        public HttpServerResponse apply(HttpServerRequest request) {
            throw HttpServerResponseException.of(405, "Method Not Allowed", HttpHeaders.of("allow", this.allowed));
        }
    }

    private static class AggregatedRequestHandler implements RequestHandler {
        private static final RequestHandler FINAL_HANDLER = (request, lastHandlerInChain) -> lastHandlerInChain.apply(request);
        private final RequestHandler chain;

        private AggregatedRequestHandler(List<HttpServerInterceptor> interceptors) {
            var chain = FINAL_HANDLER;
            for (var interceptor : interceptors) {
                var remainingChain = chain;
                chain = (r, lastHandler) -> interceptor.intercept(r, (httpServerRequest) -> remainingChain.apply(httpServerRequest, lastHandler));
            }
            this.chain = chain;
        }

        @Override
        public HttpServerResponse apply(HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
            return this.chain.apply(request, lastHandlerInChain);
        }
    }
}

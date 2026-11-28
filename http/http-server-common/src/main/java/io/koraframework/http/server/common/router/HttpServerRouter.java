package io.koraframework.http.server.common.router;

import io.koraframework.http.common.header.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.interceptor.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import org.jspecify.annotations.*;

import java.util.*;

import static io.koraframework.http.common.HttpMethod.*;

public class HttpServerRouter {

    private static final HttpServerResponse NOT_FOUND_RESPONSE = HttpServerResponse.of(404);
    private static final HttpServerRequestHandler.HandlerFunction NOT_FOUND_HANDLER = new NotFoundHandler();

    private final Map<String, HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction>> pathTemplateMatcher;
    @Nullable
    private final HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction> getMatcher;
    @Nullable
    private final HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction> postMatcher;
    @Nullable
    private final HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction> putMatcher;
    @Nullable
    private final HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction> deleteMatcher;
    private final HybridPathTemplateMatcher<MethodNotAllowedHandler> allMethodMatchers;
    private final RequestHandler requestHandler;

    public HttpServerRouter(Iterable<HttpServerRequestHandler> handlers, Iterable<HttpServerInterceptor> interceptors, HttpServerConfig config) {
        var matcherBuilders = new HashMap<String, HybridPathTemplateMatcher.Builder<HttpServerRequestHandler.HandlerFunction>>();
        var allMethodMatcherBuilder = HybridPathTemplateMatcher.<MethodNotAllowedHandler>builder();
        var routeToMethodsMap = new HashMap<String, List<String>>();

        for (var h : handlers) {
            if (!h.enabled()) {
                continue;
            }
            var route = h.routeTemplate();
            var handlerFunction = new RouteHandlerFunction(h);
            var methodMatcherBuilder = matcherBuilders.get(h.method());
            if (methodMatcherBuilder == null) {
                methodMatcherBuilder = HybridPathTemplateMatcher.builder();
                matcherBuilders.put(h.method(), methodMatcherBuilder);
            }
            var oldValue = methodMatcherBuilder.add(route, handlerFunction);
            if (oldValue != null) {
                throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(route, oldValue.getKey().templateString()));
            }

            var mainMethods = routeToMethodsMap.get(route);
            if (mainMethods == null) {
                mainMethods = new ArrayList<>(4);
                routeToMethodsMap.put(route, mainMethods);
            }
            mainMethods.add(h.method());

            if (config.ignoreTrailingSlash()) {
                var lastRouteChar = route.charAt(route.length() - 1);
                if (lastRouteChar != '*') {
                    var slashRoute = lastRouteChar == '/' ? route.substring(0, route.length() - 1) : route + '/';

                    oldValue = methodMatcherBuilder.add(slashRoute, handlerFunction);
                    if (oldValue != null) {
                        throw new IllegalStateException("Cannot add path template %s, matcher already contains an equivalent pattern %s".formatted(slashRoute, oldValue.getKey().templateString()));
                    }

                    var slashMethods = routeToMethodsMap.get(slashRoute);
                    if (slashMethods == null) {
                        slashMethods = new ArrayList<>(4);
                        routeToMethodsMap.put(slashRoute, slashMethods);
                    }
                    slashMethods.add(h.method());
                }
            }
        }

        for (var entry : routeToMethodsMap.entrySet()) {
            allMethodMatcherBuilder.add(entry.getKey(), new MethodNotAllowedHandler(entry.getValue()));
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

        interceptorsList.sort(new InterceptorComparator());

        if (interceptorsList.isEmpty()) {
            requestHandler = SimpleRequestHandler.INSTANCE;
        } else {
            requestHandler = new AggregatedRequestHandler(interceptorsList);
        }

        getMatcher = pathTemplateMatcher.get(GET);
        postMatcher = pathTemplateMatcher.get(POST);
        putMatcher = pathTemplateMatcher.get(PUT);
        deleteMatcher = pathTemplateMatcher.get(DELETE);
    }

    public HttpServerInvocation route(UnroutedHttpRequest unroutedHttpRequest) {
        HttpServerRequestHandler.HandlerFunction handlerFunction;
        Map<String, String> templateParameters;
        String pathTemplate;

        var method = unroutedHttpRequest.method();
        HybridPathTemplateMatcher<HttpServerRequestHandler.HandlerFunction> methodMatchers;
        switch (method) {
            case GET:
                methodMatchers = getMatcher;
                break;
            case POST:
                methodMatchers = postMatcher;
                break;
            case PUT:
                methodMatchers = putMatcher;
                break;
            case DELETE:
                methodMatchers = deleteMatcher;
                break;
            default:
                methodMatchers = pathTemplateMatcher.get(method);
                break;
        }

        var pathTemplateMatch = methodMatchers == null ? null : methodMatchers.match(unroutedHttpRequest.path());
        if (pathTemplateMatch == null) {
            var allMethodMatch = allMethodMatchers.match(unroutedHttpRequest.path());
            if (allMethodMatch != null) {
                handlerFunction = allMethodMatch.value();
                pathTemplate = allMethodMatch.matchedTemplate();
            } else {
                handlerFunction = NOT_FOUND_HANDLER;
                pathTemplate = null;
            }
            templateParameters = Map.of();
        } else {
            pathTemplate = pathTemplateMatch.matchedTemplate();
            handlerFunction = pathTemplateMatch.value();
            templateParameters = pathTemplateMatch.parameters();
        }
        var request = new RoutedHttpServerRequest(unroutedHttpRequest, templateParameters, pathTemplate);
        return new PublicApiInvocation(this.requestHandler, handlerFunction, request);
    }

    private static final class NotFoundHandler implements HttpServerRequestHandler.HandlerFunction {
        private final HttpServerResponse response = HttpServerResponse.of(404);

        @Override
        public HttpServerResponse apply(HttpServerRequest request) {
            return response;
        }
    }

    private static final class InterceptorComparator implements Comparator<HttpServerInterceptor> {
        @Override
        public int compare(HttpServerInterceptor o1, HttpServerInterceptor o2) {
            return o1.getClass().getName().compareTo(o2.getClass().getName());
        }
    }

    private record PublicApiInvocation(RequestHandler handler,
                                       HttpServerRequestHandler.HandlerFunction handlerFunction,
                                       HttpServerRequest routedRequest) implements HttpServerInvocation {

        public HttpServerResponse proceed(HttpServerRequest request) throws Exception {
            return handler.apply(request, handlerFunction);
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
        private final List<String> methods;
        private final String allowed;

        private MethodNotAllowedHandler(List<String> methods) {
            this.methods = methods;
            this.allowed = String.join(", ", this.methods);
        }

        @Override
        public HttpServerResponse apply(HttpServerRequest request) {
            throw HttpServerResponseException.of(405, "Method Not Allowed", HttpHeaders.of("allow", this.allowed));
        }
    }

    private static class AggregatedRequestHandler implements RequestHandler {
        private final HttpServerInterceptor[] interceptors;

        private AggregatedRequestHandler(List<HttpServerInterceptor> interceptors) {
            this.interceptors = interceptors.toArray(new HttpServerInterceptor[0]);
        }

        @Override
        public HttpServerResponse apply(HttpServerRequest request, HttpServerRequestHandler.HandlerFunction lastHandlerInChain) throws Exception {
            if (interceptors.length == 0) {
                return lastHandlerInChain.apply(request);
            }
            var executionChain = new IndexedExecutionChain(interceptors, lastHandlerInChain);
            return executionChain.process(request);
        }

        private static final class IndexedExecutionChain implements HttpServerInterceptor.InterceptChain {
            private final HttpServerInterceptor[] interceptors;
            private final HttpServerRequestHandler.HandlerFunction lastHandler;
            private int index = 0;

            public IndexedExecutionChain(HttpServerInterceptor[] interceptors, HttpServerRequestHandler.HandlerFunction lastHandler) {
                this.interceptors = interceptors;
                this.lastHandler = lastHandler;
            }

            @Override
            public HttpServerResponse process(HttpServerRequest request) throws Exception {
                if (index < interceptors.length) {
                    var currentInterceptor = interceptors[index++];
                    return currentInterceptor.intercept(request, this);
                }
                return lastHandler.apply(request);
            }
        }
    }
}

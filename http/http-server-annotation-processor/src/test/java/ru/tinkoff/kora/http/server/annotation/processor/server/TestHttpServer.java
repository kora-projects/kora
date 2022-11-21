package ru.tinkoff.kora.http.server.annotation.processor.server;

import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.annotation.processor.HttpControllerProcessor;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestHttpServer<T> {
    private final List<HttpServerRequestHandler> requestHandlers;
    public final T controller;

    public TestHttpServer(List<HttpServerRequestHandler> requestHandlers, T controller) {
        this.requestHandlers = requestHandlers;
        this.controller = controller;
    }

    @SafeVarargs
    public final HttpResponseAssert invoke(String method, String path, byte[] body, Map.Entry<String, String>... headers) {
        for (var requestHandler : requestHandlers) {
            if (!requestHandler.method().equals(method)) {
                continue;
            }
            var routeParams = this.extractRouteParams(requestHandler.routeTemplate(), path);
            if (routeParams != null) {
                var response = Mono.from(Mono.defer(() -> Mono.from(requestHandler.handle(new SimpleHttpServerRequest(method, path, body, headers, routeParams)))))
                    .onErrorResume(e -> e instanceof HttpServerResponse, e -> Mono.just((HttpServerResponse) e))
                    .switchIfEmpty(Mono.error(new RuntimeException("[eq")))
                    .block();
                return new HttpResponseAssert(response);
            }
        }
        return new HttpResponseAssert(new SimpleHttpServerResponse(404, "text/plain", HttpHeaders.of(), null));
    }

    private Map<String, String> extractRouteParams(String routeTemplate, String path) {
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf('?'));
        }
        var routeSegments = routeTemplate.split("/");
        var pathSegments = path.split("/");

        if (routeSegments.length != pathSegments.length) {
            return null;
        }
        var parameters = new HashMap<String, String>();
        for (int i = 0; i < routeSegments.length; i++) {
            var routeSegment = routeSegments[i];
            var pathSegment = pathSegments[i];
            if (routeSegment.startsWith("{") && routeSegment.endsWith("}")) {
                var paramName = routeSegment.substring(1, routeSegment.length() - 1);
                parameters.put(paramName, pathSegment);
            } else {
                if (!routeSegment.equals(pathSegment)) {
                    return null;
                }
            }
        }
        return parameters;
    }

    public static <T> TestHttpServer<T> fromController(Class<T> controller) {
        try {

            var classLoader = TestUtils.annotationProcess(controller, new HttpControllerProcessor());
            var module = classLoader.loadClass(controller.getCanonicalName() + "Module");
            var p = Proxy.newProxyInstance(classLoader, new Class[]{module}, (proxy, method, args) -> MethodHandles.privateLookupIn(module, MethodHandles.lookup())
                .in(module)
                .unreflectSpecial(method, module)
                .bindTo(proxy)
                .invokeWithArguments(args));

            var mock = Mockito.mock(controller);
            var handlers = new ArrayList<HttpServerRequestHandler>();

            for (var method : module.getMethods()) {
                if (!method.isDefault()) {
                    continue;
                }
                if (!method.getReturnType().equals(HttpServerRequestHandler.class)) {
                    continue;
                }
                var parameters = method.getGenericParameterTypes();
                if (parameters.length < 2) {
                    continue;
                }
                if (!parameters[0].equals(controller)) {
                    continue;
                }
                var callParameters = new Object[parameters.length + 1];
                var mt = MethodType.methodType(HttpServerRequestHandler.class, controller);
                callParameters[0] = p;
                callParameters[1] = mock;

                for (int i = 1; i < parameters.length; i++) {
                    var lookup = Mappers.lookupParameter(parameters[i]);
                    mt = mt.appendParameterTypes(lookup.getT1());
                    callParameters[i + 1] = lookup.getT2();
                }


                handlers.add((HttpServerRequestHandler) MethodHandles.lookup()
                    .in(module)
                    .findVirtual(module, method.getName(), mt)
                    .invokeWithArguments(callParameters)
                );
            }
            return new TestHttpServer<>(handlers, mock);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

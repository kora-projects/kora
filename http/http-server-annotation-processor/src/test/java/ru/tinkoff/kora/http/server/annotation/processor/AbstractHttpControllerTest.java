package ru.tinkoff.kora.http.server.annotation.processor;

import org.assertj.core.api.Assertions;
import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.server.annotation.processor.server.HttpResponseAssert;
import ru.tinkoff.kora.http.server.annotation.processor.server.SimpleHttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

import javax.tools.StandardLocation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

public abstract class AbstractHttpControllerTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return """
            import ru.tinkoff.kora.common.*;
            import ru.tinkoff.kora.http.server.common.annotation.*;
            import ru.tinkoff.kora.http.common.*;
            import ru.tinkoff.kora.http.common.annotation.*;
            import ru.tinkoff.kora.http.common.body.*;
            import ru.tinkoff.kora.http.common.header.*;
            import reactor.core.publisher.*;
            import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
            import ru.tinkoff.kora.http.server.common.HttpServerResponseEntity;
            import ru.tinkoff.kora.http.server.common.HttpServerResponse;
            import ru.tinkoff.kora.http.server.common.HttpServerRequest;
            import ru.tinkoff.kora.http.server.common.HttpServerInterceptor;
            import java.util.concurrent.CompletionStage;
            import java.util.concurrent.CompletableFuture;
            import jakarta.annotation.Nullable;
            import java.util.Optional;
            import static ru.tinkoff.kora.http.common.HttpMethod.GET;
            import java.util.List;
            """;
    }

    protected static class HttpControllerModule {
        private final Object moduleObject;
        private final Object controller;
        private final Class<?> moduleInterface;

        protected HttpControllerModule(Object controller, Class<?> moduleInterface, Object moduleObject) {
            this.moduleObject = moduleObject;
            this.moduleInterface = moduleInterface;
            this.controller = controller;
        }

        public Method getHandlerMethod(String name) {
            for (var method : moduleInterface.getMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            return null;
        }

        public HttpServerRequestHandler getHandler(String methodName, Object... args) {
            var fullArgs = new Object[args.length + 1];
            fullArgs[0] = this.controller;
            System.arraycopy(args, 0, fullArgs, 1, args.length);
            var method = Objects.requireNonNull(this.getHandlerMethod(methodName));

            try {
                return (HttpServerRequestHandler) method.invoke(this.moduleObject, fullArgs);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected HttpControllerModule compile(@Language("java") String... sources) {
        var compileResult = compile(List.of(new HttpControllerProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        Assertions.assertThat(compileResult.warnings()).hasSize(0);

        try {
            var moduleClass = compileResult.loadClass("ControllerModule");
            var controllerClass = compileResult.loadClass("Controller");
            var object = Proxy.newProxyInstance(compileResult.manager().getClassLoader(StandardLocation.CLASS_OUTPUT), new Class<?>[]{moduleClass}, (proxy, method, args) -> MethodHandles.privateLookupIn(moduleClass, MethodHandles.lookup())
                .in(moduleClass)
                .unreflectSpecial(method, moduleClass)
                .bindTo(proxy)
                .invokeWithArguments(args));
            var controller = controllerClass.getConstructor().newInstance();
            return new HttpControllerModule(controller, moduleClass, object);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }


    protected static byte[] read(HttpBodyOutput body) {
        var baos = new ByteArrayOutputStream(Math.max(body.contentLength(), 32));
        try {
            body.write(baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    protected HttpServerResponseMapper<String> strResponseMapper() {
        return (ctx, request, result) -> HttpServerResponse.of(200, HttpBody.plaintext(result));
    }
    protected HttpServerRequestMapper<CompletionStage<String>> asyncStringRequestMapper() {
        return request -> request.body().asArrayStage().thenApply(b -> new String(b, StandardCharsets.UTF_8));
    }
    protected HttpServerRequestMapper<String> stringRequestMapper() {
        return request -> new String(request.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    protected HttpResponseAssert assertThat(HttpServerRequestHandler handler, String method, String relativeUrl) {
        return assertThat(handler, method, relativeUrl, "");
    }

    protected HttpResponseAssert assertThat(HttpServerRequestHandler handler, String method, String relativeUrl, String body) {
        try {
            return new HttpResponseAssert(handler.handle(Context.clear(), request(method, relativeUrl, body)).toCompletableFuture().join());
        } catch (Throwable e) {
            if (e instanceof HttpServerResponse rs) {
                return new HttpResponseAssert(rs);
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    protected HttpServerRequest request(String method, String url, String body) {
        return SimpleHttpServerRequest.of(method, url, body);
    }

}

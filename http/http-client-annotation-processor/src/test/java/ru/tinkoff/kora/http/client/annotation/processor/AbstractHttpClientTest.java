package ru.tinkoff.kora.http.client.annotation.processor;

import org.intellij.lang.annotations.Language;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractHttpClientTest extends AbstractAnnotationProcessorTest {
    protected HttpClientResponse httpResponse = mock(HttpClientResponse.class);
    protected HttpClient httpClient = mock(HttpClient.class);
    protected HttpClientTelemetry telemetry = mock(HttpClientTelemetry.class);
    protected HttpClientTelemetryFactory telemetryFactory = mock(HttpClientTelemetryFactory.class);
    protected TestClient client;

    public AbstractHttpClientTest() {
        this.reset();
    }

    public void reset() {
        Mockito.reset(httpResponse, httpClient, telemetry, telemetryFactory);
        when(httpResponse.code()).thenReturn(200);
        when(httpResponse.close()).thenReturn(Mono.empty());
        when(httpResponse.body()).thenReturn(Flux.just(ByteBuffer.allocate(0)));
        when(httpClient.execute(any())).thenReturn(Mono.just(httpResponse));
    }

    protected static class TestClient {
        private final Class<?> repositoryClass;
        private final Object repositoryObject;

        protected TestClient(Class<?> clientClass, Object clientInstance) {
            this.repositoryClass = clientClass;
            this.repositoryObject = clientInstance;
        }

        @SuppressWarnings("unchecked")
        public <T> T invoke(String method, Object... args) {
            for (var repositoryClassMethod : repositoryClass.getMethods()) {
                if (repositoryClassMethod.getName().equals(method) && repositoryClassMethod.getParameters().length == args.length) {
                    try {
                        var result = repositoryClassMethod.invoke(this.repositoryObject, args);
                        if (result instanceof Mono<?> mono) {
                            return (T) mono.block();
                        }
                        if (result instanceof Future<?> future) {
                            return (T) future.get();
                        }
                        return (T) result;
                    } catch (InvocationTargetException e) {
                        if (e.getTargetException() instanceof RuntimeException re) {
                            throw re;
                        } else {
                            throw new RuntimeException(e);
                        }
                    } catch (IllegalAccessException | ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            throw new IllegalArgumentException();
        }
    }

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import ru.tinkoff.kora.http.client.common.annotation.*;
            import ru.tinkoff.kora.http.client.common.request.*;
            import ru.tinkoff.kora.http.client.common.response.*;
            import ru.tinkoff.kora.http.client.common.*;
            import ru.tinkoff.kora.http.common.annotation.*;
            import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
            import reactor.core.publisher.Mono;
            import reactor.core.publisher.Flux;
            """;
    }

    protected void compileClient(List<Object> arguments, @Language("java") String... sources) {
        compile(List.of(new HttpClientAnnotationProcessor()), sources);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }

        try {
            var clientClass = compileResult.loadClass("$TestClient_ClientImpl");
            var configParams = new Object[compileResult.loadClass("$TestClient_Config").getConstructors()[0].getParameterCount()];
            configParams[0] = "http://test-url:8080";

            var realArgs = new Object[arguments.size() + 3];
            realArgs[0] = httpClient;
            realArgs[1] = newObject("$TestClient_Config", configParams);
            realArgs[2] = telemetryFactory;
            System.arraycopy(arguments.toArray(Object[]::new), 0, realArgs, 3, arguments.size());
            for (int i = 0; i < realArgs.length; i++) {
                if (realArgs[i] instanceof GeneratedResultCallback<?> gen) {
                    realArgs[i] = gen.get();
                }
            }
            var instance = clientClass.getConstructors()[0].newInstance(realArgs);
            this.client = new TestClient(clientClass, instance);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) {
                throw re;
            } else {
                throw new RuntimeException(e.getTargetException());
            }
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

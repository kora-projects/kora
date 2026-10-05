package io.koraframework.http.client.annotation.processor;

import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import org.junit.jupiter.api.Test;
import io.koraframework.common.annotation.Component;
import io.koraframework.http.client.common.request.HttpClientParameterWriter;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.logging.common.annotation.Log;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class HttpClientCommonTest extends AbstractHttpClientTest {

    @Test
    public void testMethodAopAnnotationPreserved() {
        var mapper = mock(HttpClientResponseMapper.class);
        var client = compileClient(List.of(mapper), """
            import io.koraframework.common.annotation.Component;@Component
            @HttpClient
            public interface TestClient {
            
              @io.koraframework.logging.common.annotation.Log
              @HttpRoute(method = "POST", path = "/test")
              String request();
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
        assertThat(client.objectClass.getDeclaredMethods()[0].getDeclaredAnnotation(Log.class)).isNotNull();
    }

    @Test
    public void testMethodArgumentsAnnotationPreserved() {
        var requestMapper = mock(HttpClientRequestMapper.class);
        var responseMapper = mock(HttpClientResponseMapper.class);
        var client = compileClient(List.of(requestMapper, responseMapper), """
            import io.koraframework.common.annotation.Component;@Component
            @HttpClient
            public interface TestClient {
            
              @HttpRoute(method = "POST", path = "/test")
              String request(@io.koraframework.logging.common.annotation.Log.off String arg);
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
        assertThat(client.objectClass.getDeclaredMethods()[0].getParameters()[0].getDeclaredAnnotation(Log.off.class)).isNotNull();
    }

    @Test
    public void testOverloadedMethodsAreRejected() {
        var result = compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/a")
              void get();
              @HttpRoute(method = "GET", path = "/b/{id}")
              void get(@Path String id);
            }
            """);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors())
            .extracting(diagnostic -> diagnostic.getMessage(Locale.US))
            .anySatisfy(message -> assertThat(message).contains("HTTP client methods can't be overloaded").contains("get"));
    }

    @Test
    public void testMethodsNamedAsConstructorParameters() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/config")
              void config();
              @HttpRoute(method = "GET", path = "/httpClient")
              void httpClient();
              @HttpRoute(method = "GET", path = "/telemetryFactory")
              void telemetryFactory();
            }
            """);

        onRequest("GET", "http://test-url:8080/config", rs -> rs.withCode(200));
        client.invoke("config");
        reset(httpClient);
        onRequest("GET", "http://test-url:8080/httpClient", rs -> rs.withCode(200));
        client.invoke("httpClient");
        reset(httpClient);
        onRequest("GET", "http://test-url:8080/telemetryFactory", rs -> rs.withCode(200));
        client.invoke("telemetryFactory");
    }

    @Test
    public void testMethodsNamedAsConfigPropertiesAreRejected() {
        for (var name : List.of("url", "telemetry", "requestTimeout")) {
            var result = compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
                @HttpClient
                public interface TestClient {
                  @HttpRoute(method = "GET", path = "/test")
                  void %s();
                }
                """.formatted(name));

            assertThat(result.isFailed()).as(name).isTrue();
            assertThat(result.errors())
                .as(name)
                .extracting(diagnostic -> diagnostic.getMessage(Locale.US))
                .anySatisfy(message -> assertThat(message).contains("HTTP client method name is reserved").contains(name));
        }
    }

    @Test
    public void testParameterConverterNamesDoNotCollide() {
        HttpClientParameterWriter<Object> writer = Object::toString;
        var client = compileClient(List.of(writer, writer), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/u/{id}")
              void findUser(@Path("id") java.util.UUID id);
              @HttpRoute(method = "GET", path = "/u")
              void find(@Query("userId") java.math.BigDecimal userId);
            }
            """);

        var id = UUID.randomUUID();
        onRequest("GET", "http://test-url:8080/u/" + id, rs -> rs.withCode(200));
        client.invoke("findUser", id);
        reset(httpClient);
        onRequest("GET", "http://test-url:8080/u?userId=10", rs -> rs.withCode(200));
        client.invoke("find", BigDecimal.TEN);
    }

    @Test
    public void testGenericSuperInterfaceRouteTypesAreResolved() throws NoSuchMethodException {
        compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            public interface TestBase<T> {
              @HttpRoute(method = "GET", path = "/item")
              T get();
              @HttpRoute(method = "POST", path = "/item")
              void put(T body);
            }
            """, """
            @HttpClient
            public interface TestClient extends TestBase<String> {
            }
            """);
        compileResult.assertSuccess();

        var clientClass = compileResult.loadClass("$TestClient_ClientImpl");
        assertThat(clientClass.getMethod("get").getReturnType()).isEqualTo(String.class);
        assertThat(clientClass.getMethod("put", String.class)).isNotNull();
    }

    @Test
    public void testPrimitiveReturnType() throws IOException {
        var mapper = mock(HttpClientResponseMapper.class);
        var client = compileClient(List.of(mapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/count")
              int count();
            }
            """);

        when(mapper.apply(any())).thenReturn(42);
        onRequest("GET", "http://test-url:8080/count", rs -> rs.withCode(200));
        assertThat(client.<Integer>invoke("count")).isEqualTo(42);
    }

    @Test
    public void testParameterNamedE() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/q")
              void request(@Query("e") String e);
            }
            """);

        onRequest("GET", "http://test-url:8080/q?e=x", rs -> rs.withCode(200));
        client.invoke("request", "x");
    }
}

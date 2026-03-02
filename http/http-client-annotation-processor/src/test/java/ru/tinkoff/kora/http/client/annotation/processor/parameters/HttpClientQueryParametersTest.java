package ru.tinkoff.kora.http.client.annotation.processor.parameters;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.CompileResult;
import ru.tinkoff.kora.annotation.processor.common.ProcessingErrorException;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import ru.tinkoff.kora.http.client.annotation.processor.AbstractHttpClientTest;
import ru.tinkoff.kora.http.client.annotation.processor.HttpClientAnnotationProcessor;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class HttpClientQueryParametersTest extends AbstractHttpClientTest {
    @Test
    public void testQueryParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query String qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=test1", rs -> rs.withCode(200));
        client.invoke("request", "test1");
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=test2", rs -> rs.withCode(200));
        client.invoke("request", "test2");
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test2")));
    }

    @Test
    public void testQueryParamUnknownPathParamFails() {
        assertThrows(CompileResult.CompilationFailedException.class, () -> {
            compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()),
                """
                    @HttpClient
                    public interface TestClient {
                      @HttpRoute(method = "POST", path = "/test/{param}")
                      void request(@Query String qParam);
                    }
                    """);
            assertSuccess();
        });
    }

    @Test
    public void testQueryParamIllegalFails() {
        assertThrows(CompileResult.CompilationFailedException.class, () -> {
            compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()),
                """
                    @HttpClient
                    public interface TestClient {
                      @HttpRoute(method = "POST", path = "/test/{")
                      void request(@Query String qParam);
                    }
                    """);
            assertSuccess();
        });
    }

    @Test
    public void testIntParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query int qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=10", rs -> rs.withCode(200));
        client.invoke("request", 10);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=10")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=20", rs -> rs.withCode(200));
        client.invoke("request", 20);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=20")));
    }

    @Test
    public void testIntegerParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query Integer qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=10", rs -> rs.withCode(200));
        client.invoke("request", 10);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=10")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=20", rs -> rs.withCode(200));
        client.invoke("request", 20);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=20")));
    }

    @Test
    public void testNullableIntegerParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query @Nullable Integer qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=10", rs -> rs.withCode(200));
        client.invoke("request", 10);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=10")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", new Object[]{null});
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test")));
    }

    @Test
    public void testListQueryParameter() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.List<String> qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=test1", rs -> rs.withCode(200));
        client.invoke("request", List.of("test1"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qParam=test2", rs -> rs.withCode(200));
        client.invoke("request", List.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1&qParam=test2")));
    }

    @Test
    public void testIntegerListQueryParameter() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.List<Integer> qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=10", rs -> rs.withCode(200));
        client.invoke("request", List.of(10));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=10")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=10&qParam=20", rs -> rs.withCode(200));
        client.invoke("request", List.of(10, 20));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=10&qParam=20")));
    }

    @Test
    public void testSetQueryParameter() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.Set<String> qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=test1", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qParam=test2", rs -> rs.withCode(200));
        client.invoke("request", new LinkedHashSet<>(List.of("test1", "test2")));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1&qParam=test2")));
    }

    @Test
    public void testCollectionQueryParameter() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.Collection<String> qParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=test1", rs -> rs.withCode(200));
        client.invoke("request", new LinkedHashSet<>(List.of("test1")));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qParam=test2", rs -> rs.withCode(200));
        client.invoke("request", new LinkedHashSet<>(List.of("test1", "test2")));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1&qParam=test2")));
    }

    @Test
    public void testMapQueryParameter() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.Map<String, String> queryParams);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=test1", rs -> rs.withCode(200));
        client.invoke("request", mapOf("qParam", "test1"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qSec=test2", rs -> rs.withCode(200));
        client.invoke("request", mapOf("qParam", "test1", "qSec", "test2"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1&qSec=test2")));
    }

    @Test
    public void testMapQueryParameterWithConverter() {
        var client = compileClient(List.of((StringParameterConverter<Object>) Object::toString), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.Map<String, Object> queryParams);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?qParam=test1", rs -> rs.withCode(200));
        client.invoke("request", mapOf("qParam", "test1"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test?qParam=test1&qSec=test2", rs -> rs.withCode(200));
        client.invoke("request", mapOf("qParam", "test1", "qSec", "test2"));
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?qParam=test1&qSec=test2")));
    }

    @Test
    public void testMapQueryParameterListString() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.Map<String, java.util.List<String>> queryParams);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?q=test1", rs -> rs.withCode(200));
        var m = new LinkedHashMap<String, List<String>>();
        m.put("q", List.of("test1"));
        client.invoke("request", m);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?q=test1")));

        reset(httpClient);
        var m2 = new LinkedHashMap<String, List<String>>();
        m2.put("q", Arrays.asList("test1", null));
        m2.put("q2", Arrays.asList("test2"));
        onRequest("POST", "http://test-url:8080/test?q=test1&q&q2=test2", rs -> rs.withCode(200));
        client.invoke("request", m2);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?q=test1&q&q2=test2")));
    }

    @Test
    public void testMapQueryParameterListObject() {
        var client = compileClient(List.of((StringParameterConverter<Object>) Object::toString), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Query java.util.Map<String, java.util.List<Object>> queryParams);
            }
            """);

        onRequest("POST", "http://test-url:8080/test?q=test1", rs -> rs.withCode(200));
        var m = new LinkedHashMap<String, List<Object>>();
        m.put("q", List.of("test1"));
        client.invoke("request", m);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?q=test1")));

        reset(httpClient);
        var m2 = new LinkedHashMap<String, List<Object>>();
        m2.put("q", Arrays.asList("test1", null));
        m2.put("q2", Arrays.asList("test2"));
        onRequest("POST", "http://test-url:8080/test?q=test1&q&q2=test2", rs -> rs.withCode(200));
        client.invoke("request", m2);
        verify(httpClient).execute(argThat(r -> r.uri().toString().equals("http://test-url:8080/test?q=test1&q&q2=test2")));
    }

    private static Map<String, String> mapOf(String... kv) {
        var m = new LinkedHashMap<String, String>();
        for (int i = 0; i < kv.length; i++) {
            var k = kv[i];
            i++;
            var v = kv[i];
            m.put(k, v);
        }
        return m;
    }
}

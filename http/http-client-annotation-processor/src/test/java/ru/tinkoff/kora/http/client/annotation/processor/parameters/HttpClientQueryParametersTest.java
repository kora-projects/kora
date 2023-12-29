package ru.tinkoff.kora.http.client.annotation.processor.parameters;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.client.annotation.processor.AbstractHttpClientTest;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;

import java.util.*;

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

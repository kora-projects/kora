package ru.tinkoff.kora.http.client.annotation.processor.parameters;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.client.annotation.processor.AbstractHttpClientTest;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class HttpClientCookieParametersTest extends AbstractHttpClientTest {

    @Test
    public void testCookieParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie("some-cookie-param") String hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", "test1");
        verify(httpClient).execute(argThat(r -> r.headers().getFirst("Cookie").equals("some-cookie-param=test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", "test2");
        verify(httpClient).execute(argThat(r -> r.headers().getFirst("Cookie").equals("some-cookie-param=test2")));
    }

    @Test
    public void testListCookieParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie("some-cookie-param") java.util.List<String> hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", List.of("test1"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("some-cookie-param=test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", List.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("some-cookie-param=test1", "some-cookie-param=test2"))));
    }

    @Test
    public void testSetCookieParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie("some-cookie-param") java.util.Set<String> hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("some-cookie-param=test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> new HashSet<>(r.headers().getAll("Cookie")).equals(Set.of("some-cookie-param=test1", "some-cookie-param=test2"))));
    }

    @Test
    public void testCollectionCookieParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie("some-cookie-param") java.util.Collection<String> hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("some-cookie-param=test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> new HashSet<>(r.headers().getAll("Cookie")).equals(Set.of("some-cookie-param=test1", "some-cookie-param=test2"))));
    }

    @Test
    public void testHttpCookieParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie ru.tinkoff.kora.http.common.cookie.Cookie c1);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Cookie.of("c1", "test1"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("c1=test1"))));
    }

    @Test
    public void testMapCookieParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie java.util.Map<String, String> cookies);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Map.of("c1", "test1"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("c1=test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Map.of("c1", "test1", "c2", "test2"));
        verify(httpClient).execute(argThat(r -> new HashSet<>(r.headers().getAll("Cookie")).equals(Set.of("c1=test1", "c2=test2"))));
    }

    @Test
    public void testMapCookieParamWithConverter() {
        var client = compileClient(List.of((StringParameterConverter<Object>) Object::toString), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Cookie java.util.Map<String, Object> headers);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Map.of("c1", "test1"));
        verify(httpClient).execute(argThat(r -> r.headers().getAll("Cookie").equals(List.of("c1=test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Map.of("c2", "test2", "c1", "test1"));
        verify(httpClient).execute(argThat(r -> new HashSet<>(r.headers().getAll("Cookie")).equals(Set.of("c1=test1", "c2=test2"))));
    }
}

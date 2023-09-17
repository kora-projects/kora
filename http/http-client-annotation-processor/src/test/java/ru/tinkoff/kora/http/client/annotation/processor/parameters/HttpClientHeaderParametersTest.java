package ru.tinkoff.kora.http.client.annotation.processor.parameters;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.client.annotation.processor.AbstractHttpClientTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class HttpClientHeaderParametersTest extends AbstractHttpClientTest {

    @Test
    public void testHeaderParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Header("some-header-param") String hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", "test1");
        verify(httpClient).execute(argThat(r -> r.headers().getFirst("some-header-param").equals("test1")));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", "test2");
        verify(httpClient).execute(argThat(r -> r.headers().getFirst("some-header-param").equals("test2")));
    }

    @Test
    public void testListHeaderParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Header("some-header-param") java.util.List<String> hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", List.of("test1"));
        verify(httpClient).execute(argThat(r -> r.headers().get("some-header-param").equals(List.of("test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", List.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> r.headers().get("some-header-param").equals(List.of("test1", "test2"))));
    }

    @Test
    public void testSetHeaderParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Header("some-header-param") java.util.Set<String> hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1"));
        verify(httpClient).execute(argThat(r -> r.headers().get("some-header-param").equals(List.of("test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> new HashSet<>(r.headers().get("some-header-param")).equals(Set.of("test1", "test2"))));
    }

    @Test
    public void testCollectionHeaderParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(@Header("some-header-param") java.util.Collection<String> hParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1"));
        verify(httpClient).execute(argThat(r -> r.headers().get("some-header-param").equals(List.of("test1"))));

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", Set.of("test1", "test2"));
        verify(httpClient).execute(argThat(r -> new HashSet<>(r.headers().get("some-header-param")).equals(Set.of("test1", "test2"))));
    }
}

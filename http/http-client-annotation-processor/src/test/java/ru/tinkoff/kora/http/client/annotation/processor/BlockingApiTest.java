package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.HttpClientEncoderException;
import ru.tinkoff.kora.http.client.common.HttpClientResponseException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.body.HttpBody;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BlockingApiTest extends AbstractHttpClientTest {
    @Test
    public void testBlockingVoid() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request();
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request");

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(201));
        client.invoke("request");

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("request")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testBlockingNonVoid() throws IOException {
        var mapper = mock(HttpClientResponseMapper.class);
        compileClient(List.of(mapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              String request();
            }
            """);

        reset(httpClient, mapper);
        when(mapper.apply(any())).thenReturn("test");
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test");

        reset(httpClient, mapper);
        when(mapper.apply(any())).thenReturn("test");
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test");

        reset(httpClient, mapper);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("request")).isInstanceOf(HttpClientResponseException.class);
        verify(mapper, never()).apply(any());
    }

    @Test
    public void testBlockingCustomFinalMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String request();
            }
            """, """
            public final class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test-string-from-mapper");
    }

    @Test
    public void testBlockingCustomMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String request();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test-string-from-mapper");
    }

    @Test
    public void testBlockingFinalCodeMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public final class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("test"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testBlockingCodeMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("test"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeMapperByType() {
        compileClient(List.of(newGeneratedObject("Test200Mapper"), newGeneratedObject("Test500Mapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, type = TestResponse.Rs200.class)
              @ResponseCodeMapper(code = 500, type = TestResponse.Rs500.class)
              @HttpRoute(method = "GET", path = "/test")
              TestResponse test();
            }
            """, """
            public class Test200Mapper implements HttpClientResponseMapper<TestResponse.Rs200> {
              public TestResponse.Rs200 apply(HttpClientResponse rs) {
                  return new TestResponse.Rs200();
              }
            }
            """, """
            public class Test500Mapper implements HttpClientResponseMapper<TestResponse.Rs500> {
              public TestResponse.Rs500 apply(HttpClientResponse rs) {
                  return new TestResponse.Rs500();
              }
            }
            """, """
            public sealed interface TestResponse {
              record Rs200() implements TestResponse {}
              record Rs500() implements TestResponse {}
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        var result = client.invoke("test");
        assertThat(result).isEqualTo(newObject("TestResponse$Rs500"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        result = client.invoke("test");
        assertThat(result).isEqualTo(newObject("TestResponse$Rs200"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeMapperNoType() {
        compileClient(List.of(newGeneratedObject("TestMapper"), newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200)
              @ResponseCodeMapper(code = 500)
              @HttpRoute(method = "GET", path = "/test")
              TestResponse test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<TestResponse> {
              public TestResponse apply(HttpClientResponse rs) {
                  return rs.code() == 200 ? new TestResponse.Rs200() : new TestResponse.Rs500();
              }
            }
            """, """
            public sealed interface TestResponse {
              record Rs200() implements TestResponse {}
              record Rs500() implements TestResponse {}
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        var result = client.invoke("test");
        assertThat(result).isEqualTo(newObject("TestResponse$Rs500"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        result = client.invoke("test");
        assertThat(result).isEqualTo(newObject("TestResponse$Rs200"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeMapperNoTypeVoid() {
        compileClient(List.of((HttpClientResponseMapper<Void>) rs -> null), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 202)
              @HttpRoute(method = "GET", path = "/test")
              void test();
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(202));
        var result = client.invoke("test");
        assertThat(result).isEqualTo(null);
    }

    @Test
    public void testBlockingRequestBody() throws Exception {
        var mapper = Mockito.mock(HttpClientRequestMapper.class);
        var client = compileClient(List.of(mapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(String body);
            }
            """);

        var ctx = Context.current();

        when(mapper.apply(any(), any())).thenAnswer(invocation -> HttpBody.plaintext("test-value"));
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", "test-value");
        verify(mapper).apply(same(ctx), eq("test-value"));

        reset(httpClient, mapper);
        when(mapper.apply(any(), any()))
            .thenThrow(RuntimeException.class);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("request", "test-value")).isInstanceOf(HttpClientEncoderException.class);
        verify(mapper).apply(same(ctx), eq("test-value"));
    }

}

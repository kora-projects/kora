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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ReactiveApiTest extends AbstractHttpClientTest {
    @Test
    public void testMonoVoid() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              Mono<Void> request();
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
    public void testMonoNonVoid() throws IOException {
        var mapper = mock(HttpClientResponseMapper.class);
        compileClient(List.of(mapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              Mono<String> request();
            }
            """);

        reset(httpClient, mapper);
        when(mapper.apply(any())).thenReturn(CompletableFuture.completedFuture("test"));
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test");

        reset(httpClient, mapper);
        when(mapper.apply(any())).thenReturn(CompletableFuture.completedFuture("test"));
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test");

        reset(httpClient, mapper);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("request")).isInstanceOf(HttpClientResponseException.class);
        verify(mapper, never()).apply(any());
    }

    @Test
    public void testMonoCustomFinalMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Mono<String> request();
            }
            """, """
            public final class TestMapper implements HttpClientResponseMapper<CompletionStage<String>> {
              public CompletionStage<String> apply(HttpClientResponse rs) {
                  return CompletableFuture.completedFuture("test-string-from-mapper");
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
    public void testMonoCustomMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Mono<String> request();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<CompletionStage<String>> {
              public CompletionStage<String> apply(HttpClientResponse rs) {
                  return CompletableFuture.completedFuture("test-string-from-mapper");
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
    public void testMonoFinalCodeMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Mono<String> test();
            }
            """, """
            public final class TestMapper implements HttpClientResponseMapper<CompletionStage<String>> {
              public CompletionStage<String> apply(HttpClientResponse rs) {
                  return CompletableFuture.completedFuture("test-string-from-mapper");
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
    public void testMonoCodeMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Mono<String> test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<CompletionStage<String>> {
              public CompletionStage<String> apply(HttpClientResponse rs) {
                  return CompletableFuture.completedFuture("test-string-from-mapper");
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
              Mono<TestResponse> test();
            }
            """, """
            public class Test200Mapper implements HttpClientResponseMapper<CompletionStage<TestResponse.Rs200>> {
              public CompletionStage<TestResponse.Rs200> apply(HttpClientResponse rs) {
                  return CompletableFuture.completedFuture(new TestResponse.Rs200());
              }
            }
            """, """
            public class Test500Mapper implements HttpClientResponseMapper<CompletionStage<TestResponse.Rs500>> {
              public CompletionStage<TestResponse.Rs500> apply(HttpClientResponse rs) {
                  return CompletableFuture.completedFuture(new TestResponse.Rs500());
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
    @SuppressWarnings("enchecked")
    public void testMonoRequestBody() throws Exception {
        var mapper = Mockito.mock(HttpClientRequestMapper.class);
        var client = compileClient(List.of(mapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              Mono<Void> request(String body);
            }
            """);

        var ctx = Context.current();

        when(mapper.apply(any(), any())).thenReturn(HttpBody.plaintext("test-value"));
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

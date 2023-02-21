package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.HttpClientResponseException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpClientResponseTest extends AbstractHttpClientTest {
    @Test
    public void testSimple() {
        var mapper = mock(HttpClientResponseMapper.class);
        compileClient(List.of(mapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """);
        when(mapper.apply(any())).thenReturn(Mono.just("test-string"));

        var result = client.invoke("test");

        assertThat(result).isEqualTo("test-string");
    }

    @Test
    public void testCustomFinalMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public final class TestMapper implements HttpClientResponseMapper<String, Mono<String>> {
              public Mono<String> apply(HttpClientResponse rs) {
                  return Mono.just("test-string-from-mapper");
              }
            }
            """);

        var result = client.invoke("test");

        assertThat(result).isEqualTo("test-string-from-mapper");
    }

    @Test
    public void testCustomMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String, Mono<String>> {
              public Mono<String> apply(HttpClientResponse rs) {
                  return Mono.just("test-string-from-mapper");
              }
            }
            """);

        var result = client.invoke("test");

        assertThat(result).isEqualTo("test-string-from-mapper");
    }

    @Test
    public void testFinalCodeMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 500, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public final class TestMapper implements HttpClientResponseMapper<String, Mono<String>> {
              public Mono<String> apply(HttpClientResponse rs) {
                  return Mono.just("test-string-from-mapper");
              }
            }
            """);

        when(httpResponse.code()).thenReturn(500);
        var result = client.invoke("test");
        assertThat(result).isEqualTo("test-string-from-mapper");

        when(httpResponse.code()).thenReturn(200);
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 500, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String, Mono<String>> {
              public Mono<String> apply(HttpClientResponse rs) {
                  return Mono.just("test-string-from-mapper");
              }
            }
            """);

        when(httpResponse.code()).thenReturn(500);
        var result = client.invoke("test");
        assertThat(result).isEqualTo("test-string-from-mapper");

        when(httpResponse.code()).thenReturn(200);
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
            public class Test200Mapper implements HttpClientResponseMapper<TestResponse.Rs200, Mono<TestResponse.Rs200>> {
              public Mono<TestResponse.Rs200> apply(HttpClientResponse rs) {
                  return Mono.just(new TestResponse.Rs200());
              }
            }
            """, """
            public class Test500Mapper implements HttpClientResponseMapper<TestResponse.Rs500, Mono<TestResponse.Rs500>> {
              public Mono<TestResponse.Rs500> apply(HttpClientResponse rs) {
                  return Mono.just(new TestResponse.Rs500());
              }
            }
            """, """
            public sealed interface TestResponse {
              record Rs200() implements TestResponse {}
              record Rs500() implements TestResponse {}
            }
            """);

        when(httpResponse.code()).thenReturn(500);
        var result = client.invoke("test");
        assertThat(result).isEqualTo(newObject("TestResponse$Rs500"));

        when(httpResponse.code()).thenReturn(200);
        result = client.invoke("test");
        assertThat(result).isEqualTo(newObject("TestResponse$Rs200"));

        when(httpResponse.code()).thenReturn(201);
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }
}

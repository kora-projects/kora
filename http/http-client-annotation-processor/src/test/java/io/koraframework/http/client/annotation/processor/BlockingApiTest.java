package io.koraframework.http.client.annotation.processor;

import io.koraframework.common.annotation.Component;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.Either;
import io.koraframework.http.client.common.exception.HttpClientEncoderException;
import io.koraframework.http.client.common.exception.HttpClientException;
import io.koraframework.http.client.common.exception.HttpClientResponseException;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.http.common.body.HttpBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BlockingApiTest extends AbstractHttpClientTest {

    @Test
    public void testComponentAnnotationPreserved() {
        var client = compileClient(List.of(), """
            import io.koraframework.common.annotation.Component;@Component
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request();
            }
            """);

        Assertions.assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request");
    }

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
    public void testBlockingHttpResponseEntityEitherMapsAllStatuses() throws IOException {
        var mapper = mock(HttpClientResponseMapper.class);
        compileClient(List.of(mapper), """
            import io.koraframework.common.Either;
            import io.koraframework.http.common.HttpResponseEntity;
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              HttpResponseEntity<Either<String, String>> request();
            }
            """);

        reset(httpClient, mapper);
        when(mapper.apply(any())).thenReturn(HttpResponseEntity.of(500, io.koraframework.http.common.header.HttpHeaders.of(), Either.right("error")));
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThat(client.<HttpResponseEntity<?>>invoke("request").body()).isInstanceOf(Either.Right.class);
        verify(mapper).apply(any());
    }

    @Test
    public void testBlockingHttpResponseEntityEitherJsonTagsCompile() {
        var mapper1 = mock(HttpClientResponseMapper.class);
        var mapper2 = mock(HttpClientResponseMapper.class);
        compileClient(List.of(mapper1, mapper2), """
            import io.koraframework.common.Either;
            import io.koraframework.http.common.HttpResponseEntity;
            import io.koraframework.json.common.annotation.Json;
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/top")
              @Json HttpResponseEntity<Either<String, String>> top();
              @HttpRoute(method = "GET", path = "/nested")
              HttpResponseEntity<Either<@Json String, @Json String>> nested();
            }
            """);
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
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public final class TestMapper implements HttpClientResponseMapper<String> {
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
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public class TestMapper implements HttpClientResponseMapper<String> {
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
    public void testBlockingCustomMapperFromAbstract() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @Mapping(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String request();
            }
            """, """
            public class TestMapper extends AbstractTestMapper {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """, """
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public abstract class AbstractTestMapper implements HttpClientResponseMapper<String> {
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
    public void testBlockingCustomMapperTag() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @io.koraframework.common.annotation.Tag(TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String request();
            }
            """, """
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """);

        assertThat(client.objectClass.getConstructors()[0].getParameters()[3].getAnnotation(Tag.class))
            .isNotNull();


        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("request"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.<String>invoke("request"))
            .isInstanceOf(HttpClientException.class);
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
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public final class TestMapper implements HttpClientResponseMapper<String> {
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
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public class TestMapper implements HttpClientResponseMapper<String> {
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
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public class Test200Mapper implements HttpClientResponseMapper<TestResponse.Rs200> {
              public TestResponse.Rs200 apply(HttpClientResponse rs) {
                  return new TestResponse.Rs200();
              }
            }
            """, """
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public class Test500Mapper implements HttpClientResponseMapper<TestResponse.Rs500> {
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
            import io.koraframework.http.client.common.response.HttpClientResponseMapper;public class TestMapper implements HttpClientResponseMapper<TestResponse> {
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
        var mockMapper = Mockito.mock(HttpClientRequestMapper.class);
        var client = compileClient(List.of(mockMapper), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test")
              void request(String body);
            }
            """);

        when(mockMapper.apply(any())).thenAnswer(invocation -> HttpBody.plaintext("test-value"));
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request", "test-value");
        verify(mockMapper).apply(eq("test-value"));

        reset(httpClient, mockMapper);
        when(mockMapper.apply(any()))
            .thenThrow(RuntimeException.class);
        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("request", "test-value")).isInstanceOf(HttpClientEncoderException.class);
        verify(mockMapper).apply(eq("test-value"));
    }

    @Test
    public void testSuperinterfacesSupported() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient extends TestBase {
              @HttpRoute(method = "POST", path = "/test")
              void request();
            }
            """, """
            public interface TestBase {
              @HttpRoute(method = "POST", path = "/test1")
              void request1();
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request");
        reset(httpClient);

        onRequest("POST", "http://test-url:8080/test1", rs -> rs.withCode(200));
        client.invoke("request1");
    }

    @Test
    public void testSuperOverridesInterfacesSupported() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient extends TestBase {
              @HttpRoute(method = "POST", path = "/test")
              void request();
              @Override
              @HttpRoute(method = "POST", path = "/test2")
              void request1();
            }
            """, """
            public interface TestBase {
              @HttpRoute(method = "POST", path = "/test1")
              void request1();
            }
            """);

        onRequest("POST", "http://test-url:8080/test", rs -> rs.withCode(200));
        client.invoke("request");
        reset(httpClient);

        onRequest("POST", "http://test-url:8080/test2", rs -> rs.withCode(200));
        client.invoke("request1");
    }
}

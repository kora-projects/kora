package io.koraframework.http.client.annotation.processor;

import io.koraframework.common.Either;
import org.junit.jupiter.api.Test;
import io.koraframework.http.client.common.exception.HttpClientResponseException;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;

public class ResponseCodeMapperTest extends AbstractHttpClientTest {
    @Test
    public void testGenericResponseMapper() {
        compileClient(List.of(newGeneratedObject("TestMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @ResponseCodeMapper(code = 404, mapper = NullMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """, """
            public final class NullMapper <T> implements HttpClientResponseMapper<T> {
              public T apply(HttpClientResponse rs) {
                  return null;
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("test"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<String>invoke("test"))
            .isNull();

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeMappersByType() {
        compileClient(List.of(newGeneratedObject("Mappers$Rs1Mapper"), newGeneratedObject("Mappers$Rs2Mapper")), """
                @HttpClient
                public interface TestClient {
                  sealed interface TestResponse {
                     record Rs1() implements TestResponse {}
                     record Rs2() implements TestResponse {}
                  }
                
                  @ResponseCodeMapper(code = 201, type = TestResponse.Rs1.class)
                  @ResponseCodeMapper(code = 404, type = TestResponse.Rs2.class)
                  @HttpRoute(method = "GET", path = "/test")
                  TestResponse test();
                }
                """,
            """
                public class Mappers {
                  public static class Rs1Mapper implements HttpClientResponseMapper<TestClient.TestResponse.Rs1> {
                    @Override
                    public TestClient.TestResponse.Rs1 apply(HttpClientResponse response) {
                      return new TestClient.TestResponse.Rs1();
                    }
                  }
                  public static class Rs2Mapper implements HttpClientResponseMapper<TestClient.TestResponse.Rs2> {
                    @Override
                    public TestClient.TestResponse.Rs2 apply(HttpClientResponse response) {
                      return new TestClient.TestResponse.Rs2();
                    }
                  }
                }
                """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<Object>invoke("test"))
            .hasToString("Rs1[]");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<Object>invoke("test"))
            .hasToString("Rs2[]");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeMappersByTypeWithTag() {
        compileClient(List.of(newGeneratedObject("Mappers$Rs1Mapper"), newGeneratedObject("Mappers$Rs2Mapper")), """
                @HttpClient
                public interface TestClient {
                  sealed interface TestResponse {
                     record Rs1() implements TestResponse {}
                     record Rs2() implements TestResponse {}
                  }
                
                  @Tag(TestResponse.class)
                  @ResponseCodeMapper(code = 201, type = TestResponse.Rs1.class)
                  @ResponseCodeMapper(code = 404, type = TestResponse.Rs2.class)
                  @HttpRoute(method = "GET", path = "/test")
                  TestResponse test();
                }
                """,
            """
                public class Mappers {
                  public static class Rs1Mapper implements HttpClientResponseMapper<TestClient.TestResponse.Rs1> {
                    @Override
                    public TestClient.TestResponse.Rs1 apply(HttpClientResponse response) {
                      return new TestClient.TestResponse.Rs1();
                    }
                  }
                  public static class Rs2Mapper implements HttpClientResponseMapper<TestClient.TestResponse.Rs2> {
                    @Override
                    public TestClient.TestResponse.Rs2 apply(HttpClientResponse response) {
                      return new TestClient.TestResponse.Rs2();
                    }
                  }
                }
                """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<Object>invoke("test"))
            .hasToString("Rs1[]");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<Object>invoke("test"))
            .hasToString("Rs2[]");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testExceptionType() {
        compileClient(List.of(newGeneratedObject("TestMapper"), newGeneratedObject("ExceptionMapper")), """
            import java.util.concurrent.CompletionStage;
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @ResponseCodeMapper(code = 404, type = RuntimeException.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """, """
            public class ExceptionMapper implements HttpClientResponseMapper<RuntimeException> {
              public RuntimeException apply(HttpClientResponse rs) {
                  return new RuntimeException("test");
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("test"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThatThrownBy(() -> client.<String>invoke("test"))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("test");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testExceptionMapperType() {
        compileClient(List.of(newGeneratedObject("TestMapper"), newGeneratedObject("ExceptionMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 201, mapper = TestMapper.class)
              @ResponseCodeMapper(code = 404, mapper = ExceptionMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "test-string-from-mapper";
              }
            }
            """, """
            public class ExceptionMapper implements HttpClientResponseMapper<RuntimeException> {
              public RuntimeException apply(HttpClientResponse rs) {
                  return new RuntimeException("test");
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("test"))
            .isEqualTo("test-string-from-mapper");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThatThrownBy(() -> client.<String>invoke("test"))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("test");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testAbstractGenericResponseMapper() {
        compileClient(List.of(), """
            import io.koraframework.common.Either;@HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Either<String, Throwable> test();
            }
            """, """
            public final class Test200Mapper extends AbstractTestMapper<String, Throwable> {
              public Test200Mapper() {
                super("200-string-from-mapper");
              }
            }
            """, """
            public final class TestDefaultMapper extends AbstractTestMapper<String, Throwable> {
              public TestDefaultMapper() {
                super("default-string-from-mapper");
              }
            }
            """, """
            import io.koraframework.common.Either;public abstract class AbstractTestMapper<T, E> implements HttpClientResponseMapper<Either<T, E>> {
            
              private final T value;
            
              public AbstractTestMapper(T value) {
                this.value = value;
              }
            
              public Either<T, E> apply(HttpClientResponse rs) {
                  return Either.left(value);
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<Either<String, Throwable>>invoke("test"))
            .isEqualTo(Either.left("200-string-from-mapper"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<Either<String, Throwable>>invoke("test"))
            .isEqualTo(Either.left("default-string-from-mapper"));
    }

    @Test
    public void testComplexAbstractGenericResponseMapper() {
        compileClient(List.of(), """
            import io.koraframework.common.Either;@HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Either<String, Throwable> test();
            }
            """, """
            public final class Test200Mapper extends AbstractChildTestMapper<String, Integer, Throwable> {
              public Test200Mapper() {
                super("200-string-from-mapper");
              }
            }
            """, """
            public final class TestDefaultMapper extends AbstractChildTestMapper<String, Long, Throwable> {
              public TestDefaultMapper() {
                super("default-string-from-mapper");
              }
            }
            """, """
            public abstract class AbstractChildTestMapper<K, SHIFT, E> extends AbstractParentTestMapper<K, E, SHIFT, Double> {
            
              public AbstractChildTestMapper(K value) {
                super(value);
              }
            }
            """, """
            import io.koraframework.common.Either;public abstract class AbstractParentTestMapper<T, E, SHIFT, STATIC> implements HttpClientResponseMapper<Either<T, E>> {
            
              private final T value;
            
              public AbstractParentTestMapper(T value) {
                this.value = value;
              }
            
              public Either<T, E> apply(HttpClientResponse rs) {
                  return Either.left(value);
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<Either<String, Throwable>>invoke("test"))
            .isEqualTo(Either.left("200-string-from-mapper"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<Either<String, Throwable>>invoke("test"))
            .isEqualTo(Either.left("default-string-from-mapper"));
    }

    @Test
    public void testHalfStaticAbstractGenericResponseMapper() {
        compileClient(List.of(), """
            import io.koraframework.common.Either;@HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              Either<String, Throwable> test();
            }
            """, """
            public final class Test200Mapper extends AbstractTestMapper<String> {
              public Test200Mapper() {
                super("200-string-from-mapper");
              }
            }
            """, """
            public final class TestDefaultMapper extends AbstractTestMapper<String> {
              public TestDefaultMapper() {
                super("default-string-from-mapper");
              }
            }
            """, """
            import io.koraframework.common.Either;public abstract class AbstractTestMapper<T> implements HttpClientResponseMapper<Either<T, Throwable>> {
            
              private final T value;
            
              public AbstractTestMapper(T value) {
                this.value = value;
              }
            
              public Either<T, Throwable> apply(HttpClientResponse rs) {
                  return Either.left(value);
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<Either<String, Throwable>>invoke("test"))
            .isEqualTo(Either.left("200-string-from-mapper"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<Either<String, Throwable>>invoke("test"))
            .isEqualTo(Either.left("default-string-from-mapper"));
    }

    @Test
    public void testCodeRange() {
        compileClient(List.of(newGeneratedObject("OkMapper"), newGeneratedObject("ErrorMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper.class)
              @ResponseCodeMapper(code = 400, codeTo = 599, mapper = ErrorMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class OkMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """, """
            public class ErrorMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "error";
              }
            }
            """);

        for (var code : List.of(200, 204, 299)) {
            reset(httpClient);
            onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(code));
            assertThat(client.<String>invoke("test")).isEqualTo("ok");
        }

        for (var code : List.of(400, 500, 599)) {
            reset(httpClient);
            onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(code));
            assertThat(client.<String>invoke("test")).isEqualTo("error");
        }

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(300));
        assertThatThrownBy(() -> client.invoke("test")).isInstanceOf(HttpClientResponseException.class);
    }

    @Test
    public void testCodeRangeWithNestedExactCode() {
        compileClient(List.of(newGeneratedObject("RangeMapper"), newGeneratedObject("ExactMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = RangeMapper.class)
              @ResponseCodeMapper(code = 201, mapper = ExactMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class RangeMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "range";
              }
            }
            """, """
            public class ExactMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "exact";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(201));
        assertThat(client.<String>invoke("test")).isEqualTo("exact");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("test")).isEqualTo("range");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(202));
        assertThat(client.<String>invoke("test")).isEqualTo("range");
    }

    @Test
    public void testCodeRangeWithDefault() {
        compileClient(List.of(newGeneratedObject("OkMapper"), newGeneratedObject("DefaultMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = DefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class OkMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """, """
            public class DefaultMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "default";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<String>invoke("test")).isEqualTo("ok");

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<String>invoke("test")).isEqualTo("default");
    }

    @Test
    public void testCodeRangeWithExceptionType() {
        compileClient(List.of(newGeneratedObject("OkMapper"), newGeneratedObject("ExceptionMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = OkMapper.class)
              @ResponseCodeMapper(code = 400, codeTo = 599, mapper = ExceptionMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class OkMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """, """
            public class ExceptionMapper implements HttpClientResponseMapper<RuntimeException> {
              public RuntimeException apply(HttpClientResponse rs) {
                  return new RuntimeException("range-error");
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(500));
        assertThatThrownBy(() -> client.<String>invoke("test"))
            .isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("range-error");
    }

    @Test
    public void testCodeRangeVoid() {
        compileClient(List.of((HttpClientResponseMapper<Void>) rs -> null), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299)
              @HttpRoute(method = "GET", path = "/test")
              void test();
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(204));
        Object result = client.invoke("test");
        assertThat(result).isNull();
    }

    @Test
    public void testCodeRangeInjectedMapper() {
        compileClient(List.of(newGeneratedObject("RangeMapper")), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = RangeMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class RangeMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "injected";
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(250));
        assertThat(client.<String>invoke("test")).isEqualTo("injected");
    }

    @Test
    public void testCodeToLessThanCodeFails() {
        assertThatThrownBy(() -> compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 299, codeTo = 200, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """)).hasMessageContaining("codeTo");
    }

    @Test
    public void testCodeToWithDefaultCodeFails() {
        assertThatThrownBy(() -> compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, codeTo = 299, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """)).hasMessageContaining("codeTo");
    }

    @Test
    public void testPartialOverlapFails() {
        assertThatThrownBy(() -> compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, codeTo = 299, mapper = TestMapper.class)
              @ResponseCodeMapper(code = 250, codeTo = 350, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """)).hasMessageContaining("partially overlap");
    }

    @Test
    public void testDuplicateExactCodeFails() {
        assertThatThrownBy(() -> compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = TestMapper.class)
              @ResponseCodeMapper(code = 200, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """)).hasMessageContaining("duplicate mapping");
    }

    @Test
    public void testDuplicateDefaultFails() {
        assertThatThrownBy(() -> compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestMapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              String test();
            }
            """, """
            public class TestMapper implements HttpClientResponseMapper<String> {
              public String apply(HttpClientResponse rs) {
                  return "ok";
              }
            }
            """)).hasMessageContaining("duplicate DEFAULT");
    }
}

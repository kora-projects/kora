package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.client.common.HttpClientResponseException;

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
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              ru.tinkoff.kora.common.util.Either<String, Throwable> test();
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
            public abstract class AbstractTestMapper<T, E> implements HttpClientResponseMapper<ru.tinkoff.kora.common.util.Either<T, E>> {
            
              private final T value;
            
              public AbstractTestMapper(T value) {
                this.value = value;
              }
            
              public ru.tinkoff.kora.common.util.Either<T, E> apply(HttpClientResponse rs) {
                  return ru.tinkoff.kora.common.util.Either.left(value);
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<ru.tinkoff.kora.common.util.Either<String, Throwable>>invoke("test"))
            .isEqualTo(ru.tinkoff.kora.common.util.Either.left("200-string-from-mapper"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<ru.tinkoff.kora.common.util.Either<String, Throwable>>invoke("test"))
            .isEqualTo(ru.tinkoff.kora.common.util.Either.left("default-string-from-mapper"));
    }

    @Test
    public void testComplexAbstractGenericResponseMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              ru.tinkoff.kora.common.util.Either<String, Throwable> test();
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
            public abstract class AbstractParentTestMapper<T, E, SHIFT, STATIC> implements HttpClientResponseMapper<ru.tinkoff.kora.common.util.Either<T, E>> {
            
              private final T value;
            
              public AbstractParentTestMapper(T value) {
                this.value = value;
              }
            
              public ru.tinkoff.kora.common.util.Either<T, E> apply(HttpClientResponse rs) {
                  return ru.tinkoff.kora.common.util.Either.left(value);
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<ru.tinkoff.kora.common.util.Either<String, Throwable>>invoke("test"))
            .isEqualTo(ru.tinkoff.kora.common.util.Either.left("200-string-from-mapper"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<ru.tinkoff.kora.common.util.Either<String, Throwable>>invoke("test"))
            .isEqualTo(ru.tinkoff.kora.common.util.Either.left("default-string-from-mapper"));
    }

    @Test
    public void testHalfStaticAbstractGenericResponseMapper() {
        compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @ResponseCodeMapper(code = 200, mapper = Test200Mapper.class)
              @ResponseCodeMapper(code = ResponseCodeMapper.DEFAULT, mapper = TestDefaultMapper.class)
              @HttpRoute(method = "GET", path = "/test")
              ru.tinkoff.kora.common.util.Either<String, Throwable> test();
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
            public abstract class AbstractTestMapper<T> implements HttpClientResponseMapper<ru.tinkoff.kora.common.util.Either<T, Throwable>> {
            
              private final T value;
            
              public AbstractTestMapper(T value) {
                this.value = value;
              }
            
              public ru.tinkoff.kora.common.util.Either<T, Throwable> apply(HttpClientResponse rs) {
                  return ru.tinkoff.kora.common.util.Either.left(value);
              }
            }
            """);

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(200));
        assertThat(client.<ru.tinkoff.kora.common.util.Either<String, Throwable>>invoke("test"))
            .isEqualTo(ru.tinkoff.kora.common.util.Either.left("200-string-from-mapper"));

        reset(httpClient);
        onRequest("GET", "http://test-url:8080/test", rs -> rs.withCode(404));
        assertThat(client.<ru.tinkoff.kora.common.util.Either<String, Throwable>>invoke("test"))
            .isEqualTo(ru.tinkoff.kora.common.util.Either.left("default-string-from-mapper"));
    }
}

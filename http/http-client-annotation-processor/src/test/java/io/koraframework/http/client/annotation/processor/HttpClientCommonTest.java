package io.koraframework.http.client.annotation.processor;

import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import org.junit.jupiter.api.Test;
import io.koraframework.common.annotation.Component;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.logging.common.annotation.Log;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class HttpClientCommonTest extends AbstractHttpClientTest {

    @Test
    public void testMethodAopAnnotationPreserved() {
        var mapper = mock(HttpClientResponseMapper.class);
        var client = compileClient(List.of(mapper), """
            import io.koraframework.common.annotation.Component;@Component
            @HttpClient
            public interface TestClient {
            
              @io.koraframework.logging.common.annotation.Log
              @HttpRoute(method = "POST", path = "/test")
              String request();
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
        assertThat(client.objectClass.getDeclaredMethods()[0].getDeclaredAnnotation(Log.class)).isNotNull();
    }

    @Test
    public void testMethodArgumentsAnnotationPreserved() {
        var requestMapper = mock(HttpClientRequestMapper.class);
        var responseMapper = mock(HttpClientResponseMapper.class);
        var client = compileClient(List.of(requestMapper, responseMapper), """
            import io.koraframework.common.annotation.Component;@Component
            @HttpClient
            public interface TestClient {
            
              @HttpRoute(method = "POST", path = "/test")
              String request(@io.koraframework.logging.common.annotation.Log.off String arg);
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
        assertThat(client.objectClass.getDeclaredMethods()[0].getParameters()[0].getDeclaredAnnotation(Log.off.class)).isNotNull();
    }

    @Test
    public void testDeprecatedMethodImplementedWithoutDeprecationWarnings() {
        compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            @HttpClient
            public interface TestClient {
              /** @deprecated use v2 */
              @Deprecated
              @HttpRoute(method = "GET", path = "/v1")
              void v1();

              @HttpRoute(method = "GET", path = "/v2")
              void v2();
            }
            """);
        compileResult.assertSuccess();

        assertThat(compileResult.diagnostic())
            .noneMatch(d -> d.getCode() != null && d.getCode().contains("deprecat"));
    }

    @Test
    public void testDeprecatedClientImplementedWithoutDeprecationWarnings() {
        compile(List.of(new HttpClientAnnotationProcessor(), new ConfigParserAnnotationProcessor()), """
            /** @deprecated legacy api */
            @Deprecated
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "GET", path = "/v1")
              void v1();
            }
            """);
        compileResult.assertSuccess();

        assertThat(compileResult.diagnostic())
            .noneMatch(d -> d.getCode() != null && d.getCode().contains("deprecat"));
    }
}

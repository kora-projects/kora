package io.koraframework.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.common.Component;
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
            @Component
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
            @Component
            @HttpClient
            public interface TestClient {
            
              @HttpRoute(method = "POST", path = "/test")
              String request(@io.koraframework.logging.common.annotation.Log.off String arg);
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
        assertThat(client.objectClass.getDeclaredMethods()[0].getParameters()[0].getDeclaredAnnotation(Log.off.class)).isNotNull();
    }
}

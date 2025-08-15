package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.logging.common.annotation.Log;

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
            
              @ru.tinkoff.kora.logging.common.annotation.Log
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
              String request(@ru.tinkoff.kora.logging.common.annotation.Log String arg);
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
        assertThat(client.objectClass.getDeclaredMethods()[0].getParameters()[0].getDeclaredAnnotation(Log.class)).isNotNull();
    }
}

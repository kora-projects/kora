package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

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
            
              @ru.tinkoff.kora.validation.common.annotation.Validate
              @HttpRoute(method = "POST", path = "/test")
              String request();
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
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
              String request(@ru.tinkoff.kora.validation.common.annotation.NotBlank String arg);
            }
            """);

        assertThat(Arrays.stream(client.objectClass.getAnnotations()).anyMatch(a -> a.annotationType().equals(Component.class))).isTrue();
    }
}

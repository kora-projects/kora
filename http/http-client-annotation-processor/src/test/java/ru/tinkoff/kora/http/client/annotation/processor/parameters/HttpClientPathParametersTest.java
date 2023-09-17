package ru.tinkoff.kora.http.client.annotation.processor.parameters;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.http.client.annotation.processor.AbstractHttpClientTest;

import java.util.List;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class HttpClientPathParametersTest extends AbstractHttpClientTest {
    @Test
    public void testPathParam() {
        var client = compileClient(List.of(), """
            @HttpClient
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test/{pathParam}")
              void request(@Path String pathParam);
            }
            """);

        onRequest("POST", "http://test-url:8080/test/test1", rs -> rs.withCode(200));
        client.invoke("request", "test1");

        reset(httpClient);
        onRequest("POST", "http://test-url:8080/test/test2", rs -> rs.withCode(200));
        client.invoke("request", "test2");
    }

}

package ru.tinkoff.kora.http.client.annotation.processor;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class HttpClientInterceptorsTest extends AbstractHttpClientTest {
    @Test
    public void testInterceptors() throws Exception {
        when(httpClient.with(any())).thenCallRealMethod();
        var clientLevelInterceptor = new AtomicReference<HttpClientInterceptor>();
        var clientLevelInterceptorFactory = (GeneratedResultCallback<HttpClientInterceptor>) () -> {
            @SuppressWarnings("unchecked")
            var clazz = (Class<? extends HttpClientInterceptor>) this.compileResult.loadClass("ClientLevelInterceptor");
            clientLevelInterceptor.set(Mockito.mock(clazz));
            return clientLevelInterceptor.get();
        };
        var methodLevelInterceptor = new AtomicReference<HttpClientInterceptor>();
        var methodLevelInterceptorFactory = (GeneratedResultCallback<HttpClientInterceptor>) () -> {
            @SuppressWarnings("unchecked")
            var clazz = (Class<? extends HttpClientInterceptor>) this.compileResult.loadClass("MethodLevelInterceptor");
            methodLevelInterceptor.set(Mockito.mock(clazz));
            return methodLevelInterceptor.get();
        };
        var client = compileClient(List.of(clientLevelInterceptorFactory, methodLevelInterceptorFactory), """
            @HttpClient
            @InterceptWith(ClientLevelInterceptor.class)
            public interface TestClient {
              @HttpRoute(method = "POST", path = "/test/{pathParam}")
              @InterceptWith(MethodLevelInterceptor.class)
              void request(@Path String pathParam);
            }
            """, """
             public class ClientLevelInterceptor implements HttpClientInterceptor {
                 @Override
                 public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
                     return chain.process(request);
                 }
             }
            """, """
             public class MethodLevelInterceptor implements HttpClientInterceptor {
                 @Override
                 public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
                     return chain.process(request);
                 }
             }
            """);

        when(clientLevelInterceptor.get().processRequest(any(), any())).thenCallRealMethod();
        when(methodLevelInterceptor.get().processRequest(any(), any())).thenCallRealMethod();
        onRequest("POST", "http://test-url:8080/test/test1", rs -> rs.withCode(200));
        client.invoke("request", "test1");
        var order = Mockito.inOrder(clientLevelInterceptor.get(), methodLevelInterceptor.get());
        order.verify(clientLevelInterceptor.get()).processRequest(any(), any());
        order.verify(methodLevelInterceptor.get()).processRequest(any(), any());
        order.verifyNoMoreInteractions();
    }
}

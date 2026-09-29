package io.koraframework.http.client.common.interceptor;

import io.koraframework.http.client.common.annotation.HttpClient;
import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;

/**
 * <b>Русский</b>: Контракт перехватчика HTTP запросов клиента
 * <hr>
 * <b>English</b>: Contract of an HTTP client request interceptor
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public final class MyHttpClientInterceptor implements HttpClientInterceptor {
 *
 *    @Override
 *    public HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception {
 *      return chain.process(request);
 *    }
 * }
 *
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @InterceptWith(MyHttpClientInterceptor.class)
 *     @HttpRoute(method = HttpMethod.GET, path = "/foo/bar")
 *     HttpResponseEntity<String> get();
 * }
 * }
 * </pre>
 *
 * @see HttpClient
 */
public interface HttpClientInterceptor {

    HttpClientResponse processRequest(InterceptChain chain, HttpClientRequest request) throws Exception;

    interface InterceptChain {
        HttpClientResponse process(HttpClientRequest request) throws Exception;
    }


    static HttpClientInterceptor noop() {
        return InterceptChain::process;
    }
}

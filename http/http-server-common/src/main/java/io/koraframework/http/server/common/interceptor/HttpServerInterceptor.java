package io.koraframework.http.server.common.interceptor;

import io.koraframework.http.server.common.annotation.HttpController;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;

/**
 * <b>Русский</b>: Контракт перехватчика HTTP запросов сервера
 * <hr>
 * <b>English</b>: Contract of an HTTP server request interceptor
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public final class MyHttpServerInterceptor implements HttpServerInterceptor {
 *
 *    @Override
 *    public HttpServerResponse intercept(HttpServerRequest request, InterceptChain chain) throws Exception {
 *      return chain.process(request);
 *    }
 * }
 *
 * @HttpController
 * public class MyController {
 *
 *     @InterceptWith(MyHttpServerInterceptor.class)
 *     @HttpRoute(method = HttpMethod.GET, path = "/foo/bar")
 *     public String get() {
 *         return "OK";
 *     }
 * }
 * }
 * </pre>
 *
 * @see HttpController
 */
public interface HttpServerInterceptor {

    HttpServerResponse intercept(HttpServerRequest request, InterceptChain chain) throws Exception;

    interface InterceptChain {
        HttpServerResponse process(HttpServerRequest request) throws Exception;
    }

    static HttpServerInterceptor noop() {
        return (request, chain) -> chain.process(request);
    }
}

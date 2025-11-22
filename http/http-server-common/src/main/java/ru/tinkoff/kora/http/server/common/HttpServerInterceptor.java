package ru.tinkoff.kora.http.server.common;

/**
 * <b>Русский</b>: Аннотация позволяет указывать обработчики HTTP ответов на определенные HTTP статус коды для контроллеров
 * <hr>
 * <b>English</b>: Annotation allows you to specify HTTP response handlers for specific HTTP status codes for controllers
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public final class MyHttpServerInterceptor implements HttpServerInterceptor {
 *
 *    @Override
 *    public HttpServerResponse processRequest(HttpServerRequest request, InterceptChain chain) throws Exception {
 *      return chain.process(request);
 *    }
 * }
 *
 * @HttpClient(configPath = "my.config")
 * public interface MyHttpClient {
 *
 *     @InterceptWith(MyHttpClientInterceptor.class)
 *     @HttpRoute(method = HttpMethod.GET, path = "/foo/bar")
 *     HttpResponseEntity<String> get();
 * }
 * }
 * </pre>
 *
 * @see ru.tinkoff.kora.http.server.common.annotation.HttpController
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

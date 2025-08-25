package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.Context;

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
 *    public HttpServerResponse processRequest(Context context, HttpServerRequest request, InterceptChain chain) throws Exception {
 *      return chain.process(context, request);
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

    HttpServerResponse intercept(Context context, HttpServerRequest request, InterceptChain chain) throws Exception;

    interface InterceptChain {
        HttpServerResponse process(Context ctx, HttpServerRequest request) throws Exception;
    }

    static HttpServerInterceptor noop() {
        return (context, request, chain) -> chain.process(context, request);
    }
}

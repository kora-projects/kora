package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.annotation.HttpClient;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.CompletionStage;

/**
 * <b>Русский</b>: Аннотация позволяет указывать обработчики HTTP ответов на определенные HTTP статус коды
 * <hr>
 * <b>English</b>: Annotation allows you to specify HTTP response handlers for specific HTTP status codes
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * public final class MyHttpClientInterceptor implements HttpClientInterceptor {
 *
 *    @Override
 *    public HttpClientResponse processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) throws Exception {
 *      return chain.process(ctx, request);
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
 * @see HttpClient
 */
public interface HttpClientInterceptor {

    HttpClientResponse processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) throws Exception;

    interface InterceptChain {
        HttpClientResponse process(Context ctx, HttpClientRequest request) throws Exception;
    }


    static HttpClientInterceptor noop() {
        return (ctx, chain, request) -> chain.process(ctx, request);
    }
}

package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

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
 *    public CompletionStage<HttpServerResponse> processRequest(Context context, HttpServerRequest request, InterceptChain chain) throws Exception {
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

    CompletionStage<HttpServerResponse> intercept(Context context, HttpServerRequest request, InterceptChain chain) throws Exception;

    interface InterceptChain {
        CompletionStage<HttpServerResponse> process(Context ctx, HttpServerRequest request) throws Exception;
    }

    static HttpServerInterceptor noop() {
        return (context, request, chain) -> chain.process(context, request);
    }

    static HttpServerInterceptor wrapped(HttpServerInterceptor interceptor) {
        return (context, request, chain) -> {
            try {
                return interceptor.intercept(context, request, chain);
            } catch (CompletionException | ExecutionException e) {
                return CompletableFuture.failedFuture(e.getCause());
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }
}

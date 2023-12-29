package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

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

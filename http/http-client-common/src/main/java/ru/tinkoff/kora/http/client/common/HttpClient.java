package ru.tinkoff.kora.http.client.common;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.interceptor.HttpClientInterceptor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * <b>Русский</b>: Базовый интерфейс HTTP клиента для всех реализаций
 * <hr>
 * <b>English</b>: Basic HTTP client interface for all implementations
 */
public interface HttpClient {

    CompletionStage<HttpClientResponse> execute(HttpClientRequest request);

    default HttpClient with(HttpClientInterceptor interceptor) {
        return request -> {
            try {
                return interceptor.processRequest(Context.current(), (context, httpClientRequest) -> {
                    var ctx = Context.current();
                    try {
                        context.inject();
                        return this.execute(httpClientRequest);
                    } finally {
                        ctx.inject();
                    }
                }, request);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }
}

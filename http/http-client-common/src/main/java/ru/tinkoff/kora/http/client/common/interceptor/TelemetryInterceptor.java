package ru.tinkoff.kora.http.client.common.interceptor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;

import java.util.function.Function;

public class TelemetryInterceptor implements HttpClientInterceptor {
    private final HttpClientTelemetry telemetry;

    public TelemetryInterceptor(HttpClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public Mono<HttpClientResponse> processRequest(Function<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
        return Mono.deferContextual(rctx -> {
            var ctx = Context.Reactor.current(rctx).fork();
            ctx.inject();
            var applyResult = this.telemetry.get(ctx, request);

            return chain.apply(applyResult.request())
                .doOnError(e -> applyResult.close(null, e))
                .map(response -> applyResult.close(response, null))
                .contextWrite(c -> Context.Reactor.inject(c, ctx));
        });
    }
}

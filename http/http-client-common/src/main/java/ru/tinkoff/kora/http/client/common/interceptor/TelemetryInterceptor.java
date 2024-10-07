package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;

import java.util.concurrent.CompletionStage;

public class TelemetryInterceptor implements HttpClientInterceptor {

    private final HttpClientTelemetry telemetry;

    public TelemetryInterceptor(HttpClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public CompletionStage<HttpClientResponse> processRequest(Context ctx, InterceptChain chain, HttpClientRequest request) throws Exception {
        if (!this.telemetry.isEnabled()) {
            return chain.process(ctx, request);
        }
        try {
            var fork = ctx.fork();
            var telemetryContext = this.telemetry.get(fork, request);
            if (telemetryContext == null) {
                return chain.process(ctx, request);
            }
            fork.inject();
            return chain.process(fork, telemetryContext.request())
                .whenComplete((rs, error) -> {
                    if (error != null) {
                        var old = Context.current();
                        try {
                            fork.inject();
                            telemetryContext.close(null, error);
                        } finally {
                            old.inject();
                        }
                    }
                })
                .thenApply(rs -> {
                    var old = Context.current();
                    try {
                        fork.inject();
                        return telemetryContext.close(rs, null);
                    } finally {
                        old.inject();
                    }
                });
        } finally {
            ctx.inject();
        }
    }
}

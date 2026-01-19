package ru.tinkoff.kora.http.client.common.interceptor;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

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
            var future = new CompletableFuture<HttpClientResponse>();
            var cancelled = new AtomicReference<CancellationException>();
            future.whenComplete((ignored, throwable) -> {
                if (throwable instanceof CancellationException ce) {
                    cancelled.set(ce);
                }
            });
            chain.process(fork, telemetryContext.request()).whenComplete((rs, error) -> {
                var old = Context.current();
                try {
                    fork.inject();
                    var cancellation = cancelled.get();
                    if (cancellation != null) {
                        if (rs != null) {
                            try {
                                rs.close();
                            } catch (IOException io) {
                                cancellation.addSuppressed(io);
                            }
                        }
                        if (error != null) {
                            cancellation.addSuppressed(error);
                        }
                        var processedRs = telemetryContext.close(null, cancellation);
                        assert processedRs == null;
                        return;
                    }
                    if (error != null) {
                        telemetryContext.close(null, error);
                        future.completeExceptionally(error);
                    } else {
                        var processedRs = telemetryContext.close(rs, null);
                        future.complete(processedRs);
                    }
                } finally {
                    old.inject();
                }
            });
            return future;
        } finally {
            ctx.inject();
        }
    }
}

package ru.tinkoff.kora.http.server.common.privateapi;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;

import java.util.Objects;
import java.util.concurrent.*;

public abstract class ProbeHandler<Probe, ProbeFailure> implements HttpServerRequestHandler {
    private final All<PromiseOf<Probe>> probes;

    protected ProbeHandler(All<PromiseOf<Probe>> probes) {
        this.probes = probes;
    }

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public final HttpServerResponse handle(HttpServerRequest request) throws Exception {
        return this.handleProbes(probes);
    }

    protected abstract ProbeFailure performProbe(Probe probe) throws Exception;

    protected abstract String getMessage(ProbeFailure failure);


    private HttpServerResponse handleProbes(All<PromiseOf<Probe>> probes) {
        if (probes.isEmpty()) {
            return HttpServerResponse.of(200, HttpBody.plaintext("OK"));
        }
        var futures = new CompletableFuture<?>[probes.size()];
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < futures.length; i++) {
                var optional = probes.get(i).get();
                if (optional.isEmpty()) {
                    return HttpServerResponse.of(503, HttpBody.plaintext("Probe is not ready yet"));
                }
                var probe = optional.get();
                try {
                    var probeResult = CompletableFuture.supplyAsync(() -> {
                        try {
                            return performProbe(probe);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, executor);
                    var future = new CompletableFuture<@Nullable String>();
                    probeResult.whenComplete((result, error) -> {
                        if (error != null) {
                            future.complete("Probe failed: " + error.getMessage());
                        } else if (result != null) {
                            future.complete(getMessage(result));
                        } else {
                            future.complete(null);
                        }
                    });
                    futures[i] = future;
                } catch (Exception e) {
                    futures[i] = CompletableFuture.failedFuture(e);
                }
            }
            var resultFuture = CompletableFuture.allOf(futures).handle((r, error) -> {
                if (error != null) {
                    return HttpServerResponseException.of(error, 500, error.getMessage());
                }

                for (var future : futures) {
                    var result = future.getNow(null);
                    if (result != null) {
                        return HttpServerResponse.of(503, HttpBody.plaintext(String.valueOf(result)));
                    }
                }
                return HttpServerResponse.of(200, HttpBody.plaintext("OK"));
            });

            try {
                return resultFuture.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                return HttpServerResponse.of(408, HttpBody.plaintext("Probe failed: timeout"));
            } catch (ExecutionException e) {
                return HttpServerResponse.of(500, HttpBody.plaintext(Objects.requireNonNullElse(e.getMessage(), "")));
            }
        }
    }
}

package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

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

    @Nullable
    protected abstract ProbeFailure performProbe(Probe probe) throws Exception;

    protected abstract String getMessage(ProbeFailure failure);

    private HttpServerResponse handleProbes(All<PromiseOf<Probe>> probes) {
        var probesList = new ArrayList<PromiseOf<Probe>>();
        for (var probe : probes) {
            probesList.add(probe);
        }
        if (probesList.isEmpty()) {
            return HttpServerResponse.of(200, HttpBody.plaintext("OK"));
        }
        var futures = new CompletableFuture<?>[probesList.size()];
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < futures.length; i++) {
                var optional = probesList.get(i).get();
                if (optional.isEmpty()) {
                    return HttpServerResponse.of(503, HttpBody.plaintext("Probe is not ready yet"));
                }
                var probe = optional.get();
                try {
                    var probeResult = CompletableFuture.supplyAsync(new ProbeSupplier<>(this, probe), executor);
                    var future = new CompletableFuture<String>();
                    probeResult.whenComplete(new ProbeResultBiConsumer<>(this, future));
                    futures[i] = future;
                } catch (Exception e) {
                    futures[i] = CompletableFuture.failedFuture(e);
                }
            }
            var resultFuture = CompletableFuture.allOf(futures).handle(new ResultFutureBiFunction(futures));

            try {
                return resultFuture.get(30, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException e) {
                return HttpServerResponse.of(408, HttpBody.plaintext("Probe failed: timeout"));
            } catch (ExecutionException e) {
                return HttpServerResponse.of(500, HttpBody.plaintext(Objects.requireNonNullElse(e.getMessage(), "")));
            }
        }
    }

    private static final class ProbeSupplier<P, PF> implements Supplier<PF> {
        private final ProbeHandler<P, PF> handler;
        private final P probe;

        public ProbeSupplier(ProbeHandler<P, PF> handler, P probe) {
            this.handler = handler;
            this.probe = probe;
        }

        @Override
        public PF get() {
            try {
                return handler.performProbe(probe);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }
    }

    private static final class ProbeResultBiConsumer<PF> implements BiConsumer<PF, Throwable> {
        private final ProbeHandler<?, PF> handler;
        private final CompletableFuture<String> future;

        public ProbeResultBiConsumer(ProbeHandler<?, PF> handler, CompletableFuture<String> future) {
            this.handler = handler;
            this.future = future;
        }

        @Override
        public void accept(PF result, Throwable error) {
            if (error != null) {
                future.complete("Probe failed: " + error.getMessage());
            } else if (result != null) {
                future.complete(handler.getMessage(result));
            } else {
                future.complete(null);
            }
        }
    }

    private static final class ResultFutureBiFunction implements BiFunction<Void, Throwable, HttpServerResponse> {
        private final CompletableFuture<?>[] futures;

        public ResultFutureBiFunction(CompletableFuture<?>[] futures) {
            this.futures = futures;
        }

        @Override
        public HttpServerResponse apply(Void r, Throwable error) {
            if (error != null) {
                return HttpServerResponseException.of(error, 500, error.getMessage());
            }

            for (int i = 0; i < futures.length; i++) {
                var result = futures[i].getNow(null);
                if (result != null) {
                    return HttpServerResponse.of(503, HttpBody.plaintext(String.valueOf(result)));
                }
            }
            return HttpServerResponse.of(200, HttpBody.plaintext("OK"));
        }
    }
}

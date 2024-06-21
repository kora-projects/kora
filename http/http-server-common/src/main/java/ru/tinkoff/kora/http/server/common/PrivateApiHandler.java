package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.liveness.LivenessProbeFailure;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.telemetry.PrivateApiMetrics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

public class PrivateApiHandler {

    private static final String PROBE_FAILURE_MDC_KEY = "probeFailureMessage";
    private static final HttpServerResponse NOT_FOUND = HttpServerResponse.of(404, HttpBody.plaintext("Private API path not found"));

    private final Executor executor;
    private final ValueOf<HttpServerConfig> config;
    private final ValueOf<Optional<PrivateApiMetrics>> meterRegistry;
    private final All<PromiseOf<ReadinessProbe>> readinessProbes;
    private final All<PromiseOf<LivenessProbe>> livenessProbes;

    public PrivateApiHandler(ValueOf<HttpServerConfig> config,
                             ValueOf<Optional<PrivateApiMetrics>> meterRegistry,
                             All<PromiseOf<ReadinessProbe>> readinessProbes,
                             All<PromiseOf<LivenessProbe>> livenessProbes) {
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.readinessProbes = readinessProbes;
        this.livenessProbes = livenessProbes;
        this.executor = Objects.requireNonNullElse(VirtualThreadExecutorHolder.executor(), ForkJoinPool.commonPool());
    }

    public CompletionStage<? extends HttpServerResponse> handle(String path) {
        var metricsPath = config.get().privateApiHttpMetricsPath();
        var livenessPath = config.get().privateApiHttpLivenessPath();
        var readinessPath = config.get().privateApiHttpReadinessPath();

        var pathWithoutSlash = (path.endsWith("/"))
            ? path.substring(0, path.length() - 1)
            : path;

        var metricPathWithoutSlash = (metricsPath.endsWith("/"))
            ? metricsPath.substring(0, metricsPath.length() - 1)
            : metricsPath;
        if (pathWithoutSlash.equals(metricPathWithoutSlash) || pathWithoutSlash.startsWith(metricPathWithoutSlash + "?")) {
            return this.metrics();
        }

        var readinessPathWithoutSlash = (readinessPath.endsWith("/"))
            ? readinessPath.substring(0, readinessPath.length() - 1)
            : readinessPath;
        if (pathWithoutSlash.equals(readinessPathWithoutSlash) || pathWithoutSlash.startsWith(readinessPathWithoutSlash + "?")) {
            return this.readiness();
        }

        var livenessPathWithoutSlash = (livenessPath.endsWith("/"))
            ? livenessPath.substring(0, livenessPath.length() - 1)
            : livenessPath;
        if (pathWithoutSlash.equals(livenessPathWithoutSlash) || pathWithoutSlash.startsWith(livenessPathWithoutSlash + "?")) {
            return this.liveness();
        }

        return CompletableFuture.completedFuture(NOT_FOUND);
    }

    private CompletionStage<HttpServerResponse> metrics() {
        var response = this.meterRegistry.get()
            .map(PrivateApiMetrics::scrape)
            .orElse("");
        var body = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.plaintext(body)));
    }

    private CompletionStage<HttpServerResponse> readiness() {
        return handleProbes(readinessProbes, ReadinessProbe::probe, ReadinessProbeFailure::message);
    }

    private CompletionStage<HttpServerResponse> liveness() {
        return handleProbes(livenessProbes, LivenessProbe::probe, LivenessProbeFailure::message);
    }

    public interface TFunction<T, R> {
        R apply(T t) throws Exception;
    }

    private <Probe, Failure> CompletionStage<HttpServerResponse> handleProbes(All<PromiseOf<Probe>> probes, TFunction<Probe, Failure> performProbe, Function<Failure, String> getMessage) {
        if (probes.isEmpty()) {
            return CompletableFuture.completedFuture(HttpServerResponse.of(200, HttpBody.plaintext("OK")));
        }
        var futures = new CompletableFuture<?>[probes.size()];
        for (int i = 0; i < futures.length; i++) {
            var optional = probes.get(i).get();
            if (optional.isEmpty()) {
                return CompletableFuture.completedFuture(HttpServerResponse.of(503, HttpBody.plaintext("Probe is not ready yet")));
            }
            var probe = optional.get();
            try {
                var probeResult = CompletableFuture.supplyAsync(() -> {
                    try {
                        return performProbe.apply(probe);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }, executor);
                var future = new CompletableFuture<String>();
                probeResult.whenComplete((result, error) -> {
                    if (error != null) {
                        future.complete("Probe failed: " + error.getMessage());
                    } else if (result != null) {
                        future.complete(getMessage.apply(result));
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
            if(error != null) {
                return HttpServerResponseException.of(error, 500, error.getMessage());
            }

            for (var future : futures) {
                var result = future.getNow(null);
                if (result != null) {
                    return HttpServerResponse.of(503, HttpBody.plaintext(result.toString()));
                }
            }
            return HttpServerResponse.of(200, HttpBody.plaintext("OK"));
        });

        var timeoutFuture = CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS, executor));
        return CompletableFuture.anyOf(resultFuture, timeoutFuture).thenApply(v -> {
            if (resultFuture.isDone()) {
                return resultFuture.getNow(null);
            }
            resultFuture.cancel(true);
            return HttpServerResponse.of(408, HttpBody.plaintext("Probe failed: timeout"));
        });
    }
}

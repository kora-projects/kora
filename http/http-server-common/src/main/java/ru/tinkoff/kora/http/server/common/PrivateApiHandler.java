package ru.tinkoff.kora.http.server.common;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.PromiseOf;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.liveness.LivenessProbe;
import ru.tinkoff.kora.common.liveness.LivenessProbeFailure;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.telemetry.PrivateApiMetrics;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class PrivateApiHandler {
    private static final String PLAIN_TEXT_CONTENT_TYPE = "text/plain; charset=utf-8";
    private static final String PROBE_FAILURE_MDC_KEY = "probeFailureMessage";

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
    }

    public Publisher<? extends HttpServerResponse> handle(String path) {
        String metricsPath = config.get().privateApiHttpMetricsPath();
        String livenessPath = config.get().privateApiHttpLivenessPath();
        String readinessPath = config.get().privateApiHttpReadinessPath();

        if (path.equals(metricsPath) || path.startsWith(metricsPath + "?")) {
            return this.metrics();
        }
        if (path.equals(readinessPath) || path.startsWith(readinessPath + "?")) {
            return this.readiness();
        }
        if (path.equals(livenessPath) || path.startsWith(livenessPath + "?")) {
            return this.liveness();
        }

        return Mono.just(new SimpleHttpServerResponse(404, PLAIN_TEXT_CONTENT_TYPE, HttpHeaders.of(), ByteBuffer.wrap("Private api path not found".getBytes(StandardCharsets.UTF_8))));
    }

    private Publisher<HttpServerResponse> metrics() {
        var response = this.meterRegistry.get()
            .map(PrivateApiMetrics::scrape)
            .orElse("");
        var body = ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8));
        return Mono.just(new SimpleHttpServerResponse(200, "text/plain; charset=utf-8", HttpHeaders.of(), body));
    }

    private Publisher<HttpServerResponse> readiness() {
        return handleProbes(readinessProbes, probe -> probe.probe().map(ReadinessProbeFailure::message), () -> "GET " + config.get().privateApiHttpReadinessPath());
    }

    private Publisher<HttpServerResponse> liveness() {
        return handleProbes(livenessProbes, probe -> probe.probe().map(LivenessProbeFailure::message), () -> "GET " + config.get().privateApiHttpLivenessPath());
    }

    private <Probe> Publisher<HttpServerResponse> handleProbes(All<PromiseOf<Probe>> probes, Function<Probe, Mono<String>> performProbe, Supplier<String> operationName) {
        return Flux.fromIterable(probes)
            .<HttpServerResponse>flatMap(probePromise -> {
                var probe = probePromise.get();
                if (!probe.isPresent()) {
                    var body = ByteBuffer.wrap("Probe is not ready yet".getBytes(StandardCharsets.UTF_8));
                    return Mono.just(new SimpleHttpServerResponse(503, PLAIN_TEXT_CONTENT_TYPE, HttpHeaders.of(), body));
                }

                return performProbe.apply(probe.get()).map(failure -> {
                    var body = ByteBuffer.wrap(failure.getBytes(StandardCharsets.UTF_8));
                    return new SimpleHttpServerResponse(503, PLAIN_TEXT_CONTENT_TYPE, HttpHeaders.of(), body);
                });
            })
            .next()
            .switchIfEmpty(Mono.defer(() -> {
                var body = ByteBuffer.wrap("OK".getBytes(StandardCharsets.UTF_8));
                return Mono.just(new SimpleHttpServerResponse(200, PLAIN_TEXT_CONTENT_TYPE, HttpHeaders.of(), body));
            }))
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(err -> {
                String message = "Probe failed: " + err.getMessage();
                var body = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
                return Mono.just(new SimpleHttpServerResponse(503, PLAIN_TEXT_CONTENT_TYPE, HttpHeaders.of(), body));
            });
    }
}

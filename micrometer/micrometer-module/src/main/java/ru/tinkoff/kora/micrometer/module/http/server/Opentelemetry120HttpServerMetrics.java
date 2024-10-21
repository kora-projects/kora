package ru.tinkoff.kora.micrometer.module.http.server;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.tag.ActiveRequestsKey;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DurationKey;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Opentelemetry120HttpServerMetrics implements HttpServerMetrics {
    private final MeterRegistry meterRegistry;
    private final MicrometerHttpServerTagsProvider httpServerTagsProvider;
    private final ConcurrentHashMap<ActiveRequestsKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public Opentelemetry120HttpServerMetrics(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider, @Nullable TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.httpServerTagsProvider = httpServerTagsProvider;
        this.config = config;
    }

    @Override
    public void requestStarted(String method, String pathTemplate, String host, String scheme) {
        var counter = requestCounters.computeIfAbsent(new ActiveRequestsKey(method, pathTemplate, host, scheme), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.incrementAndGet();
    }

    @Override
    public void requestFinished(int statusCode, HttpResultCode resultCode, String scheme, String host, String method, String pathTemplate, HttpHeaders headers, long processingTimeNanos, Throwable exception) {
        var counter = requestCounters.computeIfAbsent(new ActiveRequestsKey(method, pathTemplate, host, scheme), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.decrementAndGet();
        var errorType = exception != null ? exception.getClass() : null;
        var key = new DurationKey(statusCode, method, pathTemplate, host, scheme, errorType);
        this.duration.computeIfAbsent(key, this::requestDuration)
            .record(((double) processingTimeNanos) / 1_000_000);
    }

    private void registerActiveRequestsGauge(ActiveRequestsKey key, AtomicInteger counter) {
        Gauge.builder("http.server.active_requests", counter, AtomicInteger::get)
            .tags(httpServerTagsProvider.getActiveRequestsTags(key))
            .register(this.meterRegistry);
    }

    private DistributionSummary requestDuration(DurationKey key) {
        var builder = DistributionSummary.builder("http.server.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tags(this.httpServerTagsProvider.getDurationTags(key));

        return builder.register(this.meterRegistry);
    }
}

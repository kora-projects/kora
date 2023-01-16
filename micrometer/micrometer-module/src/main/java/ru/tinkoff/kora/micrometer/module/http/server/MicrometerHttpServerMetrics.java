package ru.tinkoff.kora.micrometer.module.http.server;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetrics;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.HttpServerMetricsConfig;
import ru.tinkoff.kora.micrometer.module.http.server.tag.ActiveRequestsKey;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DurationKey;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicrometerHttpServerMetrics implements HttpServerMetrics {
    private final MeterRegistry meterRegistry;
    private final MicrometerHttpServerTagsProvider httpServerTagsProvider;
    private final ConcurrentHashMap<ActiveRequestsKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final HttpServerMetricsConfig config;

    public MicrometerHttpServerMetrics(MeterRegistry meterRegistry, MicrometerHttpServerTagsProvider httpServerTagsProvider, @Nullable HttpServerMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.httpServerTagsProvider = httpServerTagsProvider;
        this.config = config;
    }

    @Override
    public void requestStarted(String method, String target, String host, String scheme) {
        var counter = requestCounters.computeIfAbsent(new ActiveRequestsKey(method, target, host, scheme), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.incrementAndGet();
    }

    @Override
    public void requestFinished(String method, String target, String host, String scheme, int statusCode, long processingTime) {
        var counter = requestCounters.computeIfAbsent(new ActiveRequestsKey(method, target, host, scheme), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.decrementAndGet();
        this.duration.computeIfAbsent(new DurationKey(statusCode, method, target, host, scheme), this::requestDuration)
            .record(((double) processingTime) / 1_000_000);
    }

    private void registerActiveRequestsGauge(ActiveRequestsKey key, AtomicInteger counter) {
        Gauge.builder("http.server.active_requests", counter, AtomicInteger::get)
            .tags(httpServerTagsProvider.getActiveRequestsTags(key))
            .register(this.meterRegistry);
    }

    private DistributionSummary requestDuration(DurationKey key) {
        var builder = DistributionSummary.builder("http.server.duration");

        if (this.config != null && this.config.slo() != null) {
            builder.serviceLevelObjectives(this.config.slo().stream().mapToDouble(Double::doubleValue).toArray());
        } else {
            builder.serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000);
        }

        return builder
            .baseUnit("milliseconds")
            .tags(this.httpServerTagsProvider.getDurationTags(key))
            .register(this.meterRegistry);
    }
}

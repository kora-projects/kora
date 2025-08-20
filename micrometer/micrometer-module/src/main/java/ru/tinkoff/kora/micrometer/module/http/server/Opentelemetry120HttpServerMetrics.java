package ru.tinkoff.kora.micrometer.module.http.server;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerConfig;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerMetrics;
import ru.tinkoff.kora.micrometer.module.http.server.tag.ActiveRequestsKey;
import ru.tinkoff.kora.micrometer.module.http.server.tag.DurationKey;
import ru.tinkoff.kora.micrometer.module.http.server.tag.MicrometerHttpServerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Opentelemetry120HttpServerMetrics implements HttpServerMetrics {

    private final ConcurrentHashMap<ActiveRequestsKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final MicrometerHttpServerTagsProvider tagsProvider;
    private final TelemetryConfig.MetricsConfig metricsConfig;
    private final int port;

    /**
     * @see #Opentelemetry120HttpServerMetrics(MeterRegistry, MicrometerHttpServerTagsProvider, TelemetryConfig.MetricsConfig, HttpServerConfig)
     */
    @Deprecated
    public Opentelemetry120HttpServerMetrics(MeterRegistry meterRegistry,
                                             MicrometerHttpServerTagsProvider tagsProvider,
                                             TelemetryConfig.MetricsConfig metricsConfig) {
        this(meterRegistry, tagsProvider, metricsConfig, null);
    }

    public Opentelemetry120HttpServerMetrics(MeterRegistry meterRegistry,
                                             MicrometerHttpServerTagsProvider tagsProvider,
                                             TelemetryConfig.MetricsConfig metricsConfig,
                                             @Nullable HttpServerConfig serverConfig) {
        this.meterRegistry = meterRegistry;
        this.tagsProvider = tagsProvider;
        this.metricsConfig = metricsConfig;
        this.port = (serverConfig != null) ? serverConfig.publicApiHttpPort() : -1;
    }

    @Override
    public void requestStarted(String method, String pathTemplate, String host, String scheme) {
        var key = new ActiveRequestsKey(method, pathTemplate, host, port, scheme);
        var counter = requestCounters.computeIfAbsent(key, activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.incrementAndGet();
    }

    @Override
    public void requestFinished(int statusCode, HttpResultCode resultCode, String scheme, String host, String method, String pathTemplate, HttpHeaders headers, long processingTimeNanos, Throwable exception) {
        var counterKey = new ActiveRequestsKey(method, pathTemplate, host, port, scheme);
        var counter = requestCounters.computeIfAbsent(counterKey, activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.decrementAndGet();

        var errorType = exception != null ? exception.getClass() : null;
        var durationKey = new DurationKey(statusCode, method, pathTemplate, host, port, scheme, errorType);
        this.duration.computeIfAbsent(durationKey, this::requestDuration)
            .record(((double) processingTimeNanos) / 1_000_000);
    }

    private void registerActiveRequestsGauge(ActiveRequestsKey key, AtomicInteger counter) {
        Gauge.builder("http.server.active_requests", counter, AtomicInteger::get)
            .tags(tagsProvider.getActiveRequestsTags(key))
            .register(this.meterRegistry);
    }

    private DistributionSummary requestDuration(DurationKey key) {
        var builder = DistributionSummary.builder("http.server.duration")
            .serviceLevelObjectives(this.metricsConfig.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tags(this.tagsProvider.getDurationTags(key));

        return builder.register(this.meterRegistry);
    }
}

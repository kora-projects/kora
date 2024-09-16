package ru.tinkoff.kora.micrometer.module.camunda.rest;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Micrometer123CamundaRestMetrics implements CamundaRestMetrics {

    record RequestKey(String method) {}

    record DurationKey(int statusCode, String method, @Nullable Class<? extends Throwable> errorType) {}

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<RequestKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public Micrometer123CamundaRestMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void requestStarted(String method, String path) {
        var counter = requestCounters.computeIfAbsent(new RequestKey(method), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.incrementAndGet();
    }

    @Override
    public void requestFinished(String method, String path, int statusCode, long processingTimeNano, Throwable exception) {
        var counter = requestCounters.computeIfAbsent(new RequestKey(method), activeRequestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(activeRequestsKey, c);
            return c;
        });
        counter.decrementAndGet();

        var key = new DurationKey(statusCode, method, exception == null ? null : exception.getClass());
        this.duration.computeIfAbsent(key, this::requestDuration)
            .record(((double) processingTimeNano) / 1_000_000_000);
    }

    private void registerActiveRequestsGauge(RequestKey key, AtomicInteger counter) {
        Gauge.builder("camunda.rest.server.active_requests", counter, AtomicInteger::get)
            .tags(List.of(
                Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method())
            ))
            .register(this.meterRegistry);
    }

    private DistributionSummary requestDuration(DurationKey key) {
        var list = new ArrayList<Tag>(3);
        if (key.errorType() != null) {
            list.add(Tag.of(SemanticAttributes.ERROR_TYPE.getKey(), key.errorType().getCanonicalName()));
        }
        list.add(Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        list.add(Tag.of(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())));

        var builder = DistributionSummary.builder("camunda.rest.server.request.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tags(list);

        return builder.register(this.meterRegistry);
    }
}

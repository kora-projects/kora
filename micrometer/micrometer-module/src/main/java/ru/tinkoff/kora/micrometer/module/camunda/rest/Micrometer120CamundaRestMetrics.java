package ru.tinkoff.kora.micrometer.module.camunda.rest;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Micrometer120CamundaRestMetrics implements CamundaRestMetrics {

    record RequestKey(String method) {}

    record DurationKey(int statusCode, String method, @Nullable Class<? extends Throwable> errorType) {}

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<RequestKey, AtomicInteger> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final TelemetryConfig.MetricsConfig config;

    public Micrometer120CamundaRestMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public void requestStarted(String method, String path) {
        var counter = requestCounters.computeIfAbsent(new RequestKey(method), requestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(requestsKey, c);
            return c;
        });
        counter.incrementAndGet();
    }

    @Override
    public void requestFinished(String method, String path, int statusCode, long processingTimeNano, @Nullable Throwable exception) {
        var counter = requestCounters.computeIfAbsent(new RequestKey(method), requestsKey -> {
            var c = new AtomicInteger(0);
            this.registerActiveRequestsGauge(requestsKey, c);
            return c;
        });
        counter.decrementAndGet();

        var errorType = exception != null ? exception.getClass() : null;
        var key = new DurationKey(statusCode, method, errorType);

        this.duration.computeIfAbsent(key, this::requestDuration)
            .record(((double) processingTimeNano) / 1_000_000);
    }

    private void registerActiveRequestsGauge(RequestKey key, AtomicInteger counter) {
        Gauge.builder("camunda.rest.server.active_requests", counter, AtomicInteger::get)
            .tags(List.of(
                Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
                Tag.of(SemanticAttributes.HTTP_METHOD.getKey(), key.method())
            ))
            .register(this.meterRegistry);
    }

    private DistributionSummary requestDuration(DurationKey key) {
        var builder = DistributionSummary.builder("camunda.rest.server.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V120))
            .baseUnit("milliseconds")
            .tags(List.of(
                Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()),
                Tag.of(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())),
                Tag.of(SemanticAttributes.HTTP_METHOD.getKey(), key.method()),
                Tag.of(SemanticAttributes.HTTP_STATUS_CODE.getKey(), Integer.toString(key.statusCode()))
            ));

        return builder.register(this.meterRegistry);
    }
}

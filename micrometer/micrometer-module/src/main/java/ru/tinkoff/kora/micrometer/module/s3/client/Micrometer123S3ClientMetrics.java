package ru.tinkoff.kora.micrometer.module.s3.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Micrometer123S3ClientMetrics implements S3ClientMetrics {

    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;
    private final String clientName;

    public Micrometer123S3ClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String clientName) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.clientName = clientName;
    }

    @Override
    public void record(@Nullable String operation,
                       @Nullable String bucket,
                       String method,
                       int statusCode,
                       long processingTimeNanos,
                       @Nullable Throwable exception) {
        this.duration.computeIfAbsent(new DurationKey(operation, bucket, method, statusCode, exception), this::duration)
            .record((double) processingTimeNanos / 1_000_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var list = new ArrayList<Tag>(6);
        if (key.exception() != null) {
            list.add(Tag.of(SemanticAttributes.ERROR_TYPE.getKey(), key.exception().getClass().getCanonicalName()));
        }
        if (key.operation() != null) {
            list.add(Tag.of("operation", key.operation()));
        }
        if (key.bucket() != null) {
            list.add(Tag.of("bucket", key.bucket()));
        }
        list.add(Tag.of("name", clientName));
        list.add(Tag.of(SemanticAttributes.HTTP_REQUEST_METHOD.getKey(), key.method()));
        list.add(Tag.of(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE.getKey(), Integer.toString(key.statusCode())));

        var builder = DistributionSummary.builder("s3.client.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tags(list);
        return builder.register(meterRegistry);
    }

    private record DurationKey(@Nullable String operation, @Nullable String bucket, String method, int statusCode, @Nullable Throwable exception) {}
}

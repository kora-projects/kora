package ru.tinkoff.kora.micrometer.module.s3.client;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.opentelemetry.api.common.AttributeKey;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.S3Exception;
import ru.tinkoff.kora.s3.client.telemetry.S3KoraClientMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.semconv.SemanticAttributes.AWS_S3_BUCKET;
import static io.opentelemetry.semconv.SemanticAttributes.AWS_S3_KEY;

public class Opentelemetry123S3KoraClientMetrics implements S3KoraClientMetrics {

    private static final AttributeKey<String> ERROR_CODE = stringKey("aws.error.code");
    private static final AttributeKey<String> CLIENT_NAME = stringKey("aws.client.name");

    private final ConcurrentHashMap<DurationKey, DistributionSummary> duration = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;
    private final Class<?> client;

    public Opentelemetry123S3KoraClientMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, Class<?> client) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.client = client;
    }

    @Override
    public void record(String operation,
                       String bucket,
                       @Nullable String key,
                       long processingTimeNanos,
                       @Nullable S3Exception exception) {
        String errorCode = (exception != null) ? exception.getErrorCode() : null;
        this.duration.computeIfAbsent(new DurationKey(operation, bucket, key, errorCode), this::duration)
            .record((double) processingTimeNanos / 1_000_000_000);
    }

    private DistributionSummary duration(DurationKey key) {
        var list = new ArrayList<Tag>(5);
        if (key.errorCode() != null) {
            list.add(Tag.of(ERROR_CODE.getKey(), key.errorCode()));
        }
        list.add(Tag.of(CLIENT_NAME.getKey(), client.getSimpleName()));
        list.add(Tag.of(AWS_S3_BUCKET.getKey(), key.bucket()));
        list.add(Tag.of("aws.operation.name", key.operation()));
        if (key.key() != null) {
            list.add(Tag.of(AWS_S3_KEY.getKey(), key.key()));
        }

        var builder = DistributionSummary.builder("s3.kora.client.duration")
            .serviceLevelObjectives(this.config.slo(TelemetryConfig.MetricsConfig.OpentelemetrySpec.V123))
            .baseUnit("s")
            .tags(list);
        return builder.register(meterRegistry);
    }

    private record DurationKey(String operation, String bucket, @Nullable String key, @Nullable String errorCode) {}
}

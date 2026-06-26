package io.koraframework.s3.client.aws.telemetry.impl;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.AwsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultAwsS3ClientMetricsFactory {

    public static final DefaultAwsS3ClientMetricsFactory INSTANCE = new DefaultAwsS3ClientMetricsFactory();

    public DefaultAwsS3ClientMetrics create(DefaultAwsS3ClientTelemetry.TelemetryContext context) {
        return new DefaultAwsS3ClientMetrics(context);
    }

    public static class DefaultAwsS3ClientMetrics {

        public record DurationKey(String operation,
                                  String bucket,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(operation, bucket, errorType, tags);
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> requestDurationCache = new ConcurrentHashMap<>();

        protected final DefaultAwsS3ClientTelemetry.TelemetryContext context;

        public DefaultAwsS3ClientMetrics(DefaultAwsS3ClientTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(String operation, String bucket, @Nullable Throwable error, long startedRequestInNanos) {
            var took = System.nanoTime() - startedRequestInNanos;
            var key = createMetricDurationKey(operation, bucket, error);
            var meter = this.requestDurationCache.computeIfAbsent(key, _ -> createMetricClientDuration(key, operation, bucket, error).register(context.meterRegistry()));
            meter.record(took, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createMetricDurationKey(String operation, String bucket, @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new DurationKey(operation, bucket, errorType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricClientDuration(DurationKey metricKey,
                                                           String operation,
                                                           String bucket,
                                                           @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();
            var tags = new ArrayList<Tag>(7 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), "s3-aws"));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), metricKey.operation()));
            tags.add(Tag.of(AwsIncubatingAttributes.AWS_S3_BUCKET.getKey(), metricKey.bucket()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            tags.add(Tag.of(DefaultAwsS3ClientTelemetry.SYSTEM_CONFIG_PATH, this.context.clientConfigPath()));
            tags.add(Tag.of(DefaultAwsS3ClientTelemetry.SYSTEM_NAME_SIMPLE, this.context.clientSimpleName()));
            tags.add(Tag.of(DefaultAwsS3ClientTelemetry.SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName()));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("rpc.client.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(tags));
        }
    }
}

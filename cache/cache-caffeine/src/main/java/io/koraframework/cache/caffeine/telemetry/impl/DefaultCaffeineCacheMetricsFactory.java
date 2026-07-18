package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultCaffeineCacheMetricsFactory {

    public static final DefaultCaffeineCacheMetricsFactory INSTANCE = new DefaultCaffeineCacheMetricsFactory();

    public DefaultCaffeineCacheMetrics create(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
        return new DefaultCaffeineCacheMetrics(context);
    }

    public static class DefaultCaffeineCacheMetrics {

        protected static final String TAG_OPERATION = "operation";
        protected static final String TAG_ORIGIN = "origin";

        public record DurationKey(CaffeineCacheTelemetry.Operation operation,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(operation, errorType, tags);
            }
        }

        public record RatioKey(CaffeineCacheTelemetry.Operation operation,
                               RatioType ratioType,
                               @Nullable Tags extraTags) {

            public RatioKey withExtraTags(Tags tags) {
                return new RatioKey(operation, ratioType, tags);
            }
        }

        public enum RatioType {
            HIT("hit"),
            MISS("miss");

            public final String value;

            RatioType(String value) {
                this.value = value;
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> operationDurationCache = new ConcurrentHashMap<>();
        protected final ConcurrentMap<RatioKey, Counter> ratioCounterCache = new ConcurrentHashMap<>();

        protected final DefaultCaffeineCacheTelemetry.TelemetryContext context;

        public DefaultCaffeineCacheMetrics(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void reportCommandTook(CaffeineCacheTelemetry.Operation operation,
                                      long startedRecordsHandleInNanos,
                                      @Nullable Throwable error) {
            // Intentionally empty: Caffeine metrics are exported by the Caffeine inner metrics exporter.
        }

        public void reportRatioChange(CaffeineCacheTelemetry.Operation operation,
                                      RatioType ratioType,
                                      int change) {
            // Intentionally empty: Caffeine metrics are exported by the Caffeine inner metrics exporter.
        }

        protected DurationKey createMetricOperationDurationKey(CaffeineCacheTelemetry.Operation operation, @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new DurationKey(operation, errorType, null);
        }

        protected RatioKey createMetricRatioCounterKey(CaffeineCacheTelemetry.Operation operation, RatioType ratioType) {
            return new RatioKey(operation, ratioType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricOperationDuration(DurationKey metricKey,
                                                              CaffeineCacheTelemetry.Operation operation,
                                                              @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();
            var tags = new ArrayList<Tag>(6 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DefaultCaffeineCacheTelemetry.SYSTEM_CONFIG_PATH, context.cacheConfigPath()));
            tags.add(Tag.of(DefaultCaffeineCacheTelemetry.SYSTEM_NAME_SIMPLE, context.cacheImplSimpleName()));
            tags.add(Tag.of(DefaultCaffeineCacheTelemetry.SYSTEM_NAME_CANONICAL, context.cacheImplCanonicalName()));
            tags.add(Tag.of(TAG_ORIGIN, "caffeine"));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(TAG_OPERATION, operation.name()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("cache.operation.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(Tags.of(tags));
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Counter.Builder createMetricRatioCounter(RatioKey metricKey,
                                                           CaffeineCacheTelemetry.Operation operation,
                                                           RatioType ratioType) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var tags = new ArrayList<Tag>(6 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DefaultCaffeineCacheTelemetry.SYSTEM_CONFIG_PATH, context.cacheConfigPath()));
            tags.add(Tag.of(DefaultCaffeineCacheTelemetry.SYSTEM_NAME_SIMPLE, context.cacheImplSimpleName()));
            tags.add(Tag.of(DefaultCaffeineCacheTelemetry.SYSTEM_NAME_CANONICAL, context.cacheImplCanonicalName()));
            tags.add(Tag.of(TAG_ORIGIN, "caffeine"));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(TAG_OPERATION, operation.name()));
            tags.add(Tag.of("type", ratioType.value));
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Counter.builder("cache.ratio")
                .tags(Tags.of(tags));
        }
    }
}

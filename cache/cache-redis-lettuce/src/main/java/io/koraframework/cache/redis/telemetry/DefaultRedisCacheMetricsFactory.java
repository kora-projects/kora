package io.koraframework.cache.redis.telemetry;

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
import java.util.concurrent.TimeUnit;

public class DefaultRedisCacheMetricsFactory {

    public static final DefaultRedisCacheMetricsFactory INSTANCE = new DefaultRedisCacheMetricsFactory();

    public DefaultRedisCacheMetrics create(DefaultRedisCacheTelemetry.TelemetryContext context) {
        return new DefaultRedisCacheMetrics(context);
    }

    public static class DefaultRedisCacheMetrics {

        protected static final String TAG_OPERATION = "operation";
        protected static final String TAG_ORIGIN = "origin";

        public record DurationKey(String operation,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(operation, errorType, tags);
            }
        }

        public record RatioKey(String operation,
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

        protected final DefaultRedisCacheTelemetry.TelemetryContext context;

        public DefaultRedisCacheMetrics(DefaultRedisCacheTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void reportCommandTook(String operation,
                                      long startedRecordsHandleInNanos,
                                      @Nullable Throwable error) {
            var took = System.nanoTime() - startedRecordsHandleInNanos;

            var key = createMetricOperationDurationKey(operation, error);
            var meter = this.operationDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricOperationDuration(key, operation, error);
                return builder.register(context.meterRegistry());
            });

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        public void reportRatioChange(String operation,
                                      RatioType ratioType,
                                      int change) {
            var key = createMetricRatioCounterKey(operation, ratioType);
            var meter = this.ratioCounterCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRatioCounter(key, operation, ratioType);
                return builder.register(context.meterRegistry());
            });

            meter.increment(change);
        }

        protected DurationKey createMetricOperationDurationKey(String operation, @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new DurationKey(operation, errorType, null);
        }

        protected RatioKey createMetricRatioCounterKey(String operation, RatioType ratioType) {
            return new RatioKey(operation, ratioType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricOperationDuration(DurationKey metricKey,
                                                              String operation,
                                                              @Nullable Throwable error) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();
            var tags = new ArrayList<Tag>(4 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DefaultRedisCacheTelemetry.SYSTEM_NAME, context.cacheName()));
            tags.add(Tag.of(TAG_ORIGIN, "redis"));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(TAG_OPERATION, operation));
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
                                                           String operation,
                                                           RatioType ratioType) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var tags = new ArrayList<Tag>(4 + context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DefaultRedisCacheTelemetry.SYSTEM_NAME, context.cacheName()));
            tags.add(Tag.of(TAG_ORIGIN, "redis"));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(TAG_OPERATION, operation));
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

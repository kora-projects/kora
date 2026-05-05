package io.koraframework.cache.redis.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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

        public record DurationKey(String operation, @Nullable String errorType) {}

        public record RatioKey(String operation, RatioType ratioType) {}

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

            var errorValue = error == null ? "" : error.getClass().getCanonicalName();

            var key = new DurationKey(operation, errorValue);
            var meter = this.operationDurationCache.computeIfAbsent(key, _ -> {
                var builder = createMetricOperationDuration(operation, error);
                return builder.register(context.meterRegistry());
            });

            meter.record(took, TimeUnit.NANOSECONDS);
        }

        public void reportRatioChange(String operation,
                                      RatioType ratioType,
                                      int change) {
            var key = new RatioKey(operation, ratioType);
            var meter = this.ratioCounterCache.computeIfAbsent(key, _ -> {
                var builder = createMetricRatioCounter(operation, ratioType);
                return builder.register(context.meterRegistry());
            });

            meter.increment(change);
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Timer.Builder createMetricOperationDuration(String operation,
                                                              @Nullable Throwable error) {
            var errorValue = error == null ? "" : error.getClass().getCanonicalName();

            var tags = new ArrayList<Tag>(4 + context.config().metrics().tags().size());
            tags.add(Tag.of(DefaultRedisCacheTelemetry.SYSTEM_NAME, context.cacheName()));
            tags.add(Tag.of(TAG_ORIGIN, "redis"));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(TAG_OPERATION, operation));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));

            return Timer.builder("cache.operation.duration")
                .serviceLevelObjectives(context.config().metrics().slo())
                .tags(tags);
        }

        /**
         * Do Not Add Different Dynamic Tags Here, because it will cause meters incorrect recording due to cache
         */
        protected Counter.Builder createMetricRatioCounter(String operation,
                                                           RatioType ratioType) {
            var tags = new ArrayList<Tag>(4 + context.config().metrics().tags().size());
            tags.add(Tag.of(DefaultRedisCacheTelemetry.SYSTEM_NAME, context.cacheName()));
            tags.add(Tag.of(TAG_ORIGIN, "redis"));
            for (var e : context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }

            // dynamic tags from cache key
            tags.add(Tag.of(TAG_OPERATION, operation));
            tags.add(Tag.of("type", ratioType.value));

            return Counter.builder("cache.ratio")
                .tags(tags);
        }
    }
}

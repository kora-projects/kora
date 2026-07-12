package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;

public class DefaultCaffeineCacheMetricsFactory {

    public static final DefaultCaffeineCacheMetricsFactory INSTANCE = new DefaultCaffeineCacheMetricsFactory();

    public DefaultCaffeineCacheMetrics create(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
        return new DefaultCaffeineCacheMetrics(context);
    }

    public static class DefaultCaffeineCacheMetrics {

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

        protected final DefaultCaffeineCacheTelemetry.TelemetryContext context;

        public DefaultCaffeineCacheMetrics(DefaultCaffeineCacheTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void reportCommandTook(CaffeineCacheTelemetry.Operation operation,
                                      long startedRecordsHandleInNanos,
                                      @Nullable Throwable error) {}

        public void reportRatioChange(CaffeineCacheTelemetry.Operation operation,
                                      RatioType ratioType,
                                      int change) {}
    }
}

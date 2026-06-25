package io.koraframework.scheduling.common.telemetry.impl;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultSchedulingMetricsFactory {

    public static final DefaultSchedulingMetricsFactory INSTANCE = new DefaultSchedulingMetricsFactory();

    public DefaultSchedulingMetrics create(DefaultSchedulingTelemetry.TelemetryContext context) {
        return new DefaultSchedulingMetrics(context);
    }

    public static class DefaultSchedulingMetrics {

        public record DurationKey(@Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(errorType, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();

        protected final DefaultSchedulingTelemetry.TelemetryContext context;

        public DefaultSchedulingMetrics(DefaultSchedulingTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(@Nullable Throwable throwable, long durationInNanos) {
            var key = new DurationKey(throwable == null ? null : throwable.getClass(), null);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(this.context.meterRegistry()));
            meter.record(durationInNanos, TimeUnit.NANOSECONDS);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }

            var staticTags = new ArrayList<Tag>(5 + this.context.config().metrics().tags().size() + extraTags);
            staticTags.add(Tag.of(CodeAttributes.CODE_FUNCTION_NAME.getKey(), this.context.jobName()));
            if(this.context.jobConfigPath() != null) {
                staticTags.add(Tag.of(DefaultSchedulingTelemetry.SYSTEM_CONFIG_PATH, this.context.jobConfigPath()));
            }
            staticTags.add(Tag.of(DefaultSchedulingTelemetry.SYSTEM_NAME_SIMPLE, this.context.jobSimpleName()));
            staticTags.add(Tag.of(DefaultSchedulingTelemetry.SYSTEM_NAME_CANONICAL, this.context.jobCanonicalName()));
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), metricKey.errorType() == null ? "" : metricKey.errorType().getCanonicalName()));
            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Timer.builder("scheduling.job.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(staticTags));
        }
    }
}

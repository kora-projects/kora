package io.koraframework.jms.telemetry.impl;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultJmsConsumerMetricsFactory {

    public static final DefaultJmsConsumerMetricsFactory INSTANCE = new DefaultJmsConsumerMetricsFactory();

    public DefaultJmsConsumerMetrics create(DefaultJmsConsumerTelemetry.TelemetryContext context) {
        return new DefaultJmsConsumerMetrics(context);
    }

    public static class DefaultJmsConsumerMetrics {

        public record DurationKey(String destination,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(destination, errorType, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();
        protected final DefaultJmsConsumerTelemetry.TelemetryContext context;

        public DefaultJmsConsumerMetrics(DefaultJmsConsumerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordEnd(String destination, @Nullable Throwable exception, long processingTimeNanos) {
            var key = createMetricConsumerDurationKey(destination, exception);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createMetricConsumerDuration(key).register(this.context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createMetricConsumerDurationKey(String destination, @Nullable Throwable exception) {
            return new DurationKey(destination, exception == null ? null : exception.getClass(), null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricConsumerDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }

            var staticTags = new ArrayList<Tag>(3 + this.context.config().metrics().tags().size() + extraTags);
            staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_SYSTEM.getKey(), MessagingIncubatingAttributes.MessagingSystemIncubatingValues.JMS));
            staticTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), metricKey.destination()));
            var errorType = "";
            if (metricKey.errorType() != null) {
                errorType = metricKey.errorType().getCanonicalName();
                if (errorType == null) {
                    errorType = metricKey.errorType().getName();
                }
            }
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType));

            for (var tag : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(tag.getKey(), tag.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Timer.builder("messaging.receive.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(staticTags);
        }
    }
}

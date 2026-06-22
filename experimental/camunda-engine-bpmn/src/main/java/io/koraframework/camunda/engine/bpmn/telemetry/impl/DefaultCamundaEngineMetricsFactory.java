package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultCamundaEngineMetricsFactory {

    public static final DefaultCamundaEngineMetricsFactory INSTANCE = new DefaultCamundaEngineMetricsFactory();

    public DefaultCamundaEngineMetrics create(DefaultCamundaEngineTelemetry.TelemetryContext context, String javaDelegateName) {
        return new DefaultCamundaEngineMetrics(context, javaDelegateName);
    }

    public static class DefaultCamundaEngineMetrics {

        public record DurationKey(@Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(errorType, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();

        protected final DefaultCamundaEngineTelemetry.TelemetryContext context;
        protected final String javaDelegateName;

        public DefaultCamundaEngineMetrics(DefaultCamundaEngineTelemetry.TelemetryContext context, String javaDelegateName) {
            this.context = context;
            this.javaDelegateName = javaDelegateName;
        }

        public void record(@Nullable Throwable throwable, long processingTimeNanos) {
            var key = new DurationKey(throwable == null ? null : throwable.getClass(), null);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }

            var staticTags = new ArrayList<Tag>(2 + this.context.config().metrics().tags().size() + extraTags);
            var errorType = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();

            staticTags.add(Tag.of("delegate", this.javaDelegateName));
            staticTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType));

            for (var entry : this.context.config().metrics().tags().entrySet()) {
                staticTags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    staticTags.add(extraTag);
                }
            }

            return Timer.builder("camunda.engine.delegate.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(staticTags));
        }
    }
}

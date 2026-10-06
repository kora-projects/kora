package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.HookContext;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class DefaultOpenfeatureMetricsFactory {

    public static final DefaultOpenfeatureMetricsFactory INSTANCE = new DefaultOpenfeatureMetricsFactory();

    public DefaultOpenfeatureMetrics create(DefaultOpenfeatureTelemetry.TelemetryContext context) {
        return new DefaultOpenfeatureMetrics(context);
    }

    static String errorType(@Nullable FlagEvaluationDetails<?> result, @Nullable Throwable exception) {
        if (result != null && result.getErrorCode() != null) {
            return result.getErrorCode().name();
        }
        return exception != null || result == null ? ErrorCode.GENERAL.name() : "none";
    }

    public static class DefaultOpenfeatureMetrics {

        public record DurationKey(String flagKey,
                                  String flagType,
                                  String providerName,
                                  String errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(flagKey, flagType, providerName, errorType, tags);
            }
        }

        protected final ConcurrentHashMap<DurationKey, Timer> evaluationDurationCache = new ConcurrentHashMap<>();
        protected final DefaultOpenfeatureTelemetry.TelemetryContext context;

        public DefaultOpenfeatureMetrics(DefaultOpenfeatureTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordFailure(HookContext<?> evaluation,
                                  @Nullable FlagEvaluationDetails<?> result,
                                  @Nullable Throwable exception,
                                  long processingTimeNanos) {
            record(evaluation, result, exception, processingTimeNanos);
        }

        public void recordSuccess(HookContext<?> evaluation, FlagEvaluationDetails<?> result, long processingTimeNanos) {
            record(evaluation, result, null, processingTimeNanos);
        }

        protected void record(HookContext<?> evaluation,
                              @Nullable FlagEvaluationDetails<?> result,
                              @Nullable Throwable exception,
                              long processingTimeNanos) {
            var key = createMetricDurationKey(evaluation, result, exception);
            var timer = this.evaluationDurationCache.computeIfAbsent(key,
                k -> createMetricDuration(k, evaluation, result, exception).register(this.context.meterRegistry()));
            timer.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createMetricDurationKey(HookContext<?> evaluation,
                                                       @Nullable FlagEvaluationDetails<?> result,
                                                       @Nullable Throwable exception) {
            return new DurationKey(evaluation.getFlagKey(), evaluation.getType().name(),
                evaluation.getProviderMetadata().getName(), errorType(result, exception), null);
        }

        // Dynamic tags must also appear in DurationKey to avoid meter cache collisions.
        protected Timer.Builder createMetricDuration(DurationKey key,
                                                      HookContext<?> evaluation,
                                                      @Nullable FlagEvaluationDetails<?> result,
                                                      @Nullable Throwable exception) {
            var tags = new ArrayList<Tag>();
            tags.add(Tag.of("feature_flag.key", key.flagKey()));
            tags.add(Tag.of("feature_flag.type", key.flagType()));
            tags.add(Tag.of("feature_flag.provider.name", key.providerName()));
            tags.add(Tag.of("error.type", key.errorType()));
            tags.add(Tag.of(DefaultOpenfeatureTelemetry.SYSTEM_CONFIG_PATH, this.context.clientConfigPath()));
            tags.add(Tag.of(DefaultOpenfeatureTelemetry.SYSTEM_NAME_SIMPLE, this.context.clientSimpleName()));
            tags.add(Tag.of(DefaultOpenfeatureTelemetry.SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName()));
            this.context.config().metrics().tags().forEach((keyName, value) -> tags.add(Tag.of(keyName, value)));
            if (key.extraTags() != null) {
                key.extraTags().forEach(tags::add);
            }
            return Timer.builder("feature_flag.evaluation.duration")
                .tags(tags)
                .serviceLevelObjectives(this.context.config().metrics().slo());
        }
    }
}

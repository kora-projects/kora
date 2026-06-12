package io.koraframework.camunda.zeebe.worker.telemetry.impl;

import io.camunda.zeebe.client.api.response.ActivatedJob;
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

public class DefaultZeebeWorkerMetricsFactory {

    public static final DefaultZeebeWorkerMetricsFactory INSTANCE = new DefaultZeebeWorkerMetricsFactory();

    public DefaultZeebeWorkerMetrics create(DefaultZeebeWorkerTelemetry.TelemetryContext context) {
        return new DefaultZeebeWorkerMetrics(context);
    }

    public static class DefaultZeebeWorkerMetrics {

        public record DurationKey(String workerType,
                                  String jobType,
                                  @Nullable Class<? extends Throwable> errorType,
                                  boolean failedByUser,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(workerType, jobType, errorType, failedByUser, tags);
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> durationCache = new ConcurrentHashMap<>();

        protected final DefaultZeebeWorkerTelemetry.TelemetryContext context;

        public DefaultZeebeWorkerMetrics(DefaultZeebeWorkerTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(ActivatedJob job, @Nullable Throwable error, boolean failedByUser, long processingTimeNanos) {
            var key = createDurationKey(job, error, failedByUser);
            var meter = this.durationCache.computeIfAbsent(key, _ -> createDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createDurationKey(ActivatedJob job, @Nullable Throwable error, boolean failedByUser) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new DurationKey(this.context.workerType(), job.getType(), errorType, failedByUser, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null
                ? (metricKey.failedByUser ? "ErrorStep" : "")
                : metricKey.errorType.getCanonicalName();
            var tags = new ArrayList<Tag>(3 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of("job.name", metricKey.workerType()));
            tags.add(Tag.of("job.type", metricKey.jobType()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("zeebe.worker.handler.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(tags));
        }
    }
}

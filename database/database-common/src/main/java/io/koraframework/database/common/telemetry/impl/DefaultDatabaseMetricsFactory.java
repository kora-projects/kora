package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.QueryContext;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.DbAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class DefaultDatabaseMetricsFactory {

    public static final DefaultDatabaseMetricsFactory INSTANCE = new DefaultDatabaseMetricsFactory();

    public DefaultDatabaseMetrics create(DefaultDatabaseTelemetry.TelemetryContext context) {
        return new DefaultDatabaseMetrics(context);
    }

    public static class DefaultDatabaseMetrics {

        public record DurationKey(String queryId,
                                  String operation,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public DurationKey withExtraTags(Tags tags) {
                return new DurationKey(queryId, operation, errorType, tags);
            }
        }

        protected final ConcurrentMap<DurationKey, Timer> operationDurationCache = new ConcurrentHashMap<>();

        protected final DefaultDatabaseTelemetry.TelemetryContext context;

        public DefaultDatabaseMetrics(DefaultDatabaseTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void record(QueryContext query, @Nullable Throwable error, long processingTimeNanos) {
            var key = createMetricDurationKey(query, error);
            var meter = this.operationDurationCache.computeIfAbsent(key, _ -> createMetricOperationDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        protected DurationKey createMetricDurationKey(QueryContext query, @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new DurationKey(query.queryId(), query.operation(), errorType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricOperationDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();
            var tags = new ArrayList<Tag>(5 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), this.context.poolName()));
            tags.add(Tag.of(DbAttributes.DB_SYSTEM_NAME.getKey(), this.context.dbSystem()));
            tags.add(Tag.of(DbAttributes.DB_QUERY_TEXT.getKey(), metricKey.queryId()));
            tags.add(Tag.of(DbAttributes.DB_OPERATION_NAME.getKey(), metricKey.operation()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("db.client.operation.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(tags));
        }
    }
}

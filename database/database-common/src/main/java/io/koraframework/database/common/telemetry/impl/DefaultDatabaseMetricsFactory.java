package io.koraframework.database.common.telemetry.impl;

import io.koraframework.database.common.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.*;
import io.opentelemetry.semconv.incubating.*;
import org.jspecify.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

public class DefaultDatabaseMetricsFactory {

    public static final DefaultDatabaseMetricsFactory INSTANCE = new DefaultDatabaseMetricsFactory();

    public DefaultDatabaseMetrics create(DefaultDatabaseTelemetry.TelemetryContext context) {
        return new DefaultDatabaseMetrics(context);
    }

    public static class DefaultDatabaseMetrics {

        private static final ThreadLocal<MutableDurationKey> LOOKUP_KEY = ThreadLocal.withInitial(new MutableDurationKeySupplier());

        protected final ConcurrentMap<Object, Timer> operationDurationCache = new ConcurrentHashMap<>();
        protected final DefaultDatabaseTelemetry.TelemetryContext context;
        protected final Tag[] staticMetricTags;

        public DefaultDatabaseMetrics(DefaultDatabaseTelemetry.TelemetryContext context) {
            this.context = context;

            var tagsConfig = context.config().metrics().tags();
            if (tagsConfig != null && !tagsConfig.isEmpty()) {
                this.staticMetricTags = new Tag[tagsConfig.size()];
                int idx = 0;
                for (var entry : tagsConfig.entrySet()) {
                    this.staticMetricTags[idx++] = Tag.of(entry.getKey(), entry.getValue());
                }
            } else {
                this.staticMetricTags = new Tag[0];
            }
        }

        public void record(QueryContext query, @Nullable Throwable error, long processingTimeNanos) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            final Class<? extends Throwable> errorType = error == null ? null : error.getClass();

            final MutableDurationKey lookupKey = LOOKUP_KEY.get();
            lookupKey.queryId = query.queryId();
            lookupKey.operation = query.operation();
            lookupKey.errorType = errorType;

            Timer meter = this.operationDurationCache.get(lookupKey);

            if (meter == null) {
                var permanentKey = new DurationKey(query.queryId(), query.operation(), errorType, null);
                var newMeter = createMetricOperationDuration(permanentKey).register(context.meterRegistry());
                var oldMeter = this.operationDurationCache.putIfAbsent(permanentKey, newMeter);
                meter = (oldMeter != null) ? oldMeter : newMeter;
            }

            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricOperationDuration(DurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (var _ : metricKey.extraTags) {
                    extraTags++;
                }
            }

            String errorValue = "";
            if (metricKey.errorType != null) {
                errorValue = metricKey.errorType.getCanonicalName();
                if (errorValue == null) {
                    errorValue = metricKey.errorType.getName();
                }
            }

            var tags = new ArrayList<Tag>(5 + this.staticMetricTags.length + extraTags);
            tags.add(Tag.of(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), this.context.poolName()));
            tags.add(Tag.of(DbAttributes.DB_SYSTEM_NAME.getKey(), this.context.dbSystem()));
            tags.add(Tag.of(DbAttributes.DB_QUERY_TEXT.getKey(), metricKey.queryId()));
            tags.add(Tag.of(DbAttributes.DB_OPERATION_NAME.getKey(), metricKey.operation()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));

            for (int i = 0; i < this.staticMetricTags.length; i++) {
                tags.add(this.staticMetricTags[i]);
            }

            if (metricKey.extraTags != null) {
                for (var tag : metricKey.extraTags) {
                    tags.add(tag);
                }
            }

            return Timer.builder("db.client.operation.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(tags));
        }

        public record DurationKey(String queryId,
                                  String operation,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {}

        private static final class MutableDurationKey {
            String queryId;
            String operation;
            Class<? extends Throwable> errorType;

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof DurationKey that)) return false;
                return Objects.equals(queryId, that.queryId) &&
                    Objects.equals(operation, that.operation) &&
                    Objects.equals(errorType, that.errorType) &&
                    that.extraTags == null;
            }

            @Override
            public int hashCode() {
                int result = queryId != null ? queryId.hashCode() : 0;
                result = 31 * result + (operation != null ? operation.hashCode() : 0);
                result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
                return result;
            }
        }

        private static final class MutableDurationKeySupplier implements Supplier<MutableDurationKey> {
            @Override
            public MutableDurationKey get() {
                return new MutableDurationKey();
            }
        }
    }
}

package io.koraframework.database.jdbc.telemetry.impl;

import io.koraframework.database.jdbc.telemetry.JdbcTransactionContext;
import io.micrometer.core.instrument.Gauge;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultJdbcDatabaseMetricsFactory {

    public static final DefaultJdbcDatabaseMetricsFactory INSTANCE = new DefaultJdbcDatabaseMetricsFactory();

    public DefaultJdbcDatabaseMetrics create(DefaultJdbcDatabaseTelemetry.TelemetryContext context) {
        return new DefaultJdbcDatabaseMetrics(context);
    }

    public static class DefaultJdbcDatabaseMetrics {

        public record TransactionDurationKey(String isolationLevel,
                                  @Nullable Class<? extends Throwable> errorType,
                                  @Nullable Tags extraTags) {

            public TransactionDurationKey withExtraTags(Tags tags) {
                return new TransactionDurationKey(isolationLevel, errorType, tags);
            }
        }

        public record ActiveTransactionsKey(String isolationLevel,
                                            @Nullable Tags extraTags) {

            public ActiveTransactionsKey withExtraTags(Tags tags) {
                return new ActiveTransactionsKey(isolationLevel, tags);
            }
        }

        protected final ConcurrentHashMap<TransactionDurationKey, Timer> transactionDurationCache = new ConcurrentHashMap<>();
        protected final ConcurrentHashMap<ActiveTransactionsKey, AtomicLong> activeTransactionsCache = new ConcurrentHashMap<>();

        protected final DefaultJdbcDatabaseTelemetry.TelemetryContext context;

        public DefaultJdbcDatabaseMetrics(DefaultJdbcDatabaseTelemetry.TelemetryContext context) {
            this.context = context;
        }

        public void recordTransactionStart(JdbcTransactionContext transaction) {
            createMetricActiveTransactionsGaugeCounter(transaction).incrementAndGet();
        }

        public void recordTransactionEnd(JdbcTransactionContext transaction, @Nullable Throwable error, long processingTimeNanos) {
            var key = createMetricTransactionDurationKey(transaction, error);
            var meter = this.transactionDurationCache.computeIfAbsent(key, _ -> createMetricTransactionDuration(key).register(context.meterRegistry()));
            meter.record(processingTimeNanos, TimeUnit.NANOSECONDS);
            createMetricActiveTransactionsGaugeCounter(transaction).decrementAndGet();
        }

        protected TransactionDurationKey createMetricTransactionDurationKey(JdbcTransactionContext transaction, @Nullable Throwable error) {
            if (error instanceof CompletionException ce && ce.getCause() != null) {
                error = ce.getCause();
            }
            var errorType = error == null ? null : error.getClass();
            return new TransactionDurationKey(transaction.isolationLevel(), errorType, null);
        }

        // DO NOT ADD DYNAMIC TAGS IN BUILDER, use metric key instead of metric collision will happen
        protected Timer.Builder createMetricTransactionDuration(TransactionDurationKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var errorValue = metricKey.errorType == null ? "" : metricKey.errorType.getCanonicalName();
            var tags = new ArrayList<Tag>(4 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), this.context.poolName()));
            tags.add(Tag.of(DbAttributes.DB_SYSTEM_NAME.getKey(), this.context.dbSystem()));
            tags.add(Tag.of(DefaultJdbcDatabaseTelemetry.DB_TRANSACTION_ISOLATION_LEVEL, metricKey.isolationLevel()));
            tags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            return Timer.builder("db.client.transaction.duration")
                .serviceLevelObjectives(this.context.config().metrics().slo())
                .tags(Tags.of(tags));
        }

        protected ActiveTransactionsKey createMetricActiveTransactionsGaugeKey(JdbcTransactionContext transaction) {
            return new ActiveTransactionsKey(transaction.isolationLevel(), null);
        }

        protected AtomicLong createMetricActiveTransactionsGaugeCounter(JdbcTransactionContext transaction) {
            var key = createMetricActiveTransactionsGaugeKey(transaction);
            return this.activeTransactionsCache.computeIfAbsent(key, this::createMetricActiveTransactions);
        }

        protected AtomicLong createMetricActiveTransactions(ActiveTransactionsKey metricKey) {
            var extraTags = 0;
            if (metricKey.extraTags != null) {
                for (Tag _ : metricKey.extraTags) {
                    extraTags++;
                }
            }
            var tags = new ArrayList<Tag>(3 + this.context.config().metrics().tags().size() + extraTags);
            tags.add(Tag.of(DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME.getKey(), this.context.poolName()));
            tags.add(Tag.of(DbAttributes.DB_SYSTEM_NAME.getKey(), this.context.dbSystem()));
            tags.add(Tag.of(DefaultJdbcDatabaseTelemetry.DB_TRANSACTION_ISOLATION_LEVEL, metricKey.isolationLevel()));
            for (var e : this.context.config().metrics().tags().entrySet()) {
                tags.add(Tag.of(e.getKey(), e.getValue()));
            }
            if (metricKey.extraTags != null) {
                for (Tag extraTag : metricKey.extraTags) {
                    tags.add(extraTag);
                }
            }

            var value = new AtomicLong(0);
            Gauge.builder("db.client.transaction.active", value, AtomicLong::get)
                .tags(tags)
                .register(this.context.meterRegistry());
            return value;
        }
    }
}

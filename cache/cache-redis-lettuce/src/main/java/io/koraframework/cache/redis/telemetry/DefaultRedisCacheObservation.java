package io.koraframework.cache.redis.telemetry;

import io.koraframework.cache.redis.telemetry.DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics.RatioType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class DefaultRedisCacheObservation implements RedisCacheObservation {

    protected final long operationStarted = System.nanoTime();

    protected final String operation;
    protected final DefaultRedisCacheTelemetry.TelemetryContext context;
    protected final DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics metrics;
    protected final Span span;

    @Nullable
    protected Throwable error;
    @Nullable
    protected Object value;
    @Nullable
    protected Map<?, ?> values;
    @Nullable
    protected Object key;
    @Nullable
    protected Collection<?> keys;

    public DefaultRedisCacheObservation(String operation,
                                        DefaultRedisCacheTelemetry.TelemetryContext context,
                                        DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics metrics,
                                        Span span) {
        this.operation = operation;
        this.context = context;
        this.metrics = metrics;
        this.span = span;
    }

    @Override
    public void observeKey(Object key) {
        this.context.logger().atTrace()
            .addKeyValue("operation", this.operation)
            .addKeyValue("cacheName", this.context.cacheName())
            .addKeyValue("key", key)
            .log("Redis Cache operation started...");
        this.key = key;
    }

    @Override
    public void observeKeys(Collection<?> keys) {
        this.context.logger().atTrace()
            .addKeyValue("operation", this.operation)
            .addKeyValue("cacheName", this.context.cacheName())
            .addKeyValue("keys", keys.size())
            .log("Redis Cache operation started...");
        this.keys = keys;
    }

    @Override
    public void observeValue(Object value) {
        this.value = value;
    }

    @Override
    public void observeValues(Map<?, ?> keyToValue) {
        this.values = keyToValue;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        if (error != null) {
            this.context.logger().atWarn()
                .addKeyValue("operation", this.operation)
                .addKeyValue("cacheName", this.context.cacheName())
                .log("Redis Cache operation failed due to: {}", error.getMessage());

            metrics.reportCommandTook(operation, operationStarted, error);
            span.end();
            return;
        }

        span.setStatus(StatusCode.OK);
        metrics.reportCommandTook(operation, operationStarted, null);

        if (operation.startsWith("GET")) {
            if (value != null) {
                metrics.reportRatioChange(operation, RatioType.HIT, 1);
            } else if (values != null && !values.isEmpty()) {
                metrics.reportRatioChange(operation, RatioType.HIT, values.size());
            } else if (key != null) {
                metrics.reportRatioChange(operation, RatioType.MISS, 1);
            } else if (keys != null) {
                metrics.reportRatioChange(operation, RatioType.MISS, keys.size());
            }

            if (value == null && (values == null || values.isEmpty())) {
                this.context.logger().atTrace()
                    .addKeyValue("operation", this.operation)
                    .addKeyValue("cacheName", this.context.cacheName())
                    .log("Redis Cache operation didn't retried value");
            } else {
                var retried = value != null ? 1 : values.size();
                this.context.logger().atDebug()
                    .addKeyValue("operation", this.operation)
                    .addKeyValue("cacheName", this.context.cacheName())
                    .addKeyValue("retried", retried)
                    .log("Redis Cache operation retried value");
            }
        } else {
            this.context.logger().atTrace()
                .addKeyValue("operation", this.operation)
                .addKeyValue("cacheName", this.context.cacheName())
                .log("Redis Cache operation completed");
        }
        span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}

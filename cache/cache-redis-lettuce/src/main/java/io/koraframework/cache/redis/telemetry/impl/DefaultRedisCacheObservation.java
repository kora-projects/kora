package io.koraframework.cache.redis.telemetry.impl;

import io.koraframework.cache.redis.telemetry.RedisCacheObservation;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics.RatioType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class DefaultRedisCacheObservation implements RedisCacheObservation {

    protected final long operationStarted = System.nanoTime();

    protected final String operation;
    protected final DefaultRedisCacheTelemetry.TelemetryContext context;
    protected final DefaultRedisCacheLoggerFactory.DefaultRedisCacheLogger logger;
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
                                        DefaultRedisCacheLoggerFactory.DefaultRedisCacheLogger logger,
                                        DefaultRedisCacheMetricsFactory.DefaultRedisCacheMetrics metrics,
                                        Span span) {
        this.operation = operation;
        this.context = context;
        this.logger = logger;
        this.metrics = metrics;
        this.span = span;
    }

    @Override
    public void observeKey(Object key) {
        this.key = key;
        this.logger.logStart(this.operation, key);
    }

    @Override
    public void observeKeys(Collection<?> keys) {
        this.keys = keys;
        this.logger.logStart(this.operation, keys);
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
            metrics.reportCommandTook(operation, operationStarted, error);
            logger.logEnd(operation, operationStarted, error);
            span.end();
            return;
        }

        span.setStatus(StatusCode.OK);
        metrics.reportCommandTook(operation, operationStarted, null);

        var retrieved = 0;
        var missed = 0;
        if (operation.startsWith("GET")) {
            if (value != null) {
                metrics.reportRatioChange(operation, RatioType.HIT, 1);
                retrieved = 1;
            } else if (values != null && !values.isEmpty()) {
                metrics.reportRatioChange(operation, RatioType.HIT, values.size());
                retrieved = values.size();
            } else if (key != null) {
                metrics.reportRatioChange(operation, RatioType.MISS, 1);
                missed = 1;
            } else if (keys != null) {
                metrics.reportRatioChange(operation, RatioType.MISS, keys.size());
                missed = keys.size();
            }
        }
        logger.logEnd(operation, operationStarted, retrieved, missed);
        span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}

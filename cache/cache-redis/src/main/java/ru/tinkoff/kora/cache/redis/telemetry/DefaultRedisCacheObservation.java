package ru.tinkoff.kora.cache.redis.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DefaultRedisCacheObservation implements RedisCacheObservation {
    private final long start = System.nanoTime();
    protected final String operation;
    protected final String cacheName;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> duration;
    protected final Meter.MeterProvider<Counter> ratio;
    protected final Logger logger;
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

    public DefaultRedisCacheObservation(String operation, String cacheName, Span span, Meter.MeterProvider<Timer> duration, Meter.MeterProvider<Counter> ratio, Logger logger) {
        this.operation = operation;
        this.cacheName = cacheName;
        this.span = span;
        this.duration = duration;
        this.ratio = ratio;
        this.logger = logger;
    }

    @Override
    public void observeKey(Object key) {
        logger.trace("Operation '{}' for cache '{}' started", operation, cacheName);
        this.key = key;
    }

    @Override
    public void observeKeys(Collection<?> keys) {
        logger.trace("Operation '{}' for cache '{}' started", operation, cacheName);
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
            logger.warn("Operation '{}' failed for cache '{}'", operation, cacheName, error);
            duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), error.getClass().getCanonicalName())
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            span.end();
            return;
        }
        span.setStatus(StatusCode.OK);
        duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), "")
            .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        if (operation.startsWith("GET")) {
            if (value != null) {
                ratio.withTag("type", "hit").increment();
            } else if (values != null && !values.isEmpty()) {
                ratio.withTag("type", "hit").increment(values.size());
            } else if (key != null) {
                ratio.withTag("type", "miss").increment();
            } else if (keys != null) {
                ratio.withTag("type", "miss").increment(keys.size());
            }
            if (value == null && (values == null || values.isEmpty())) {
                logger.trace("Operation '{}' for cache '{}' didn't retried value", operation, cacheName);
            } else {
                logger.debug("Operation '{}' for cache '{}' retried value", operation, cacheName);
            }
        } else {
            logger.trace("Operation '{}' for cache '{}' completed", operation, cacheName);
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

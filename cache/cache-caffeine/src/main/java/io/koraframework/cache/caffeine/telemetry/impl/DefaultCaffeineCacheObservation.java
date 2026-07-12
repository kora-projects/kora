package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheObservation;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class DefaultCaffeineCacheObservation implements CaffeineCacheObservation {

    protected final CaffeineCacheTelemetry.Operation operation;
    protected final DefaultCaffeineCacheLoggerFactory.DefaultCaffeineCacheLogger logger;

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

    public DefaultCaffeineCacheObservation(CaffeineCacheTelemetry.Operation operation,
                                           DefaultCaffeineCacheLoggerFactory.DefaultCaffeineCacheLogger logger) {
        this.operation = operation;
        this.logger = logger;
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
        return Span.getInvalid();
    }

    @Override
    public void end() {
        if (error != null) {
            return;
        }

        var retrieved = 0;
        var missed = 0;
        if (operation.hasCacheResult()) {
            if (value != null) {
                retrieved = 1;
            } else if (values != null && !values.isEmpty()) {
                retrieved = values.size();
            } else if (key != null) {
                missed = 1;
            } else if (keys != null) {
                missed = keys.size();
            }
        }
        logger.logEnd(operation, retrieved, missed);
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.logger.logEnd(operation, e);
    }
}

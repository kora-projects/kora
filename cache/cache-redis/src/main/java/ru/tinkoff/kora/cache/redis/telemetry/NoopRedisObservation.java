package ru.tinkoff.kora.cache.redis.telemetry;

import io.opentelemetry.api.trace.Span;

import java.util.Collection;
import java.util.Map;

public class NoopRedisObservation implements RedisCacheObservation {
    public static final NoopRedisObservation INSTANCE = new NoopRedisObservation();

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }

    @Override
    public void observeKey(Object key) {

    }

    @Override
    public void observeValue(Object value) {

    }

    @Override
    public void observeKeys(Collection<?> keys) {

    }

    @Override
    public void observeValues(Map<?, ?> keyToValue) {

    }
}

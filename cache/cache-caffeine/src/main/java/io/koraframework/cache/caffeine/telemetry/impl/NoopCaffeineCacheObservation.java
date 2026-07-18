package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheObservation;
import io.opentelemetry.api.trace.Span;

import java.util.Collection;
import java.util.Map;

public final class NoopCaffeineCacheObservation implements CaffeineCacheObservation {

    public static final NoopCaffeineCacheObservation INSTANCE = new NoopCaffeineCacheObservation();

    private NoopCaffeineCacheObservation() {}

    @Override
    public void observeKey(Object key) {}

    @Override
    public void observeValue(Object value) {}

    @Override
    public void observeKeys(Collection<?> keys) {}

    @Override
    public void observeValues(Map<?, ?> keyToValue) {}

    @Override
    public void observeError(Throwable e) {}

    @Override
    public void end() {}

    @Override
    public Span span() {
        return Span.getInvalid();
    }
}

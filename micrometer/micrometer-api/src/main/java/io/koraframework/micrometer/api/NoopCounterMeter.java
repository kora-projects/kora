package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Tags;

import java.util.function.Supplier;

public final class NoopCounterMeter implements CounterMeter {

    public static final NoopCounterMeter INSTANCE = new NoopCounterMeter();

    private NoopCounterMeter() {}

    @Override
    public void recordIncrement(long increment, Supplier<Tags> metricCacheKeyTags) {

    }

    @Override
    public void recordIncrement(long increment, Tags metricCacheKeyTags) {

    }
}

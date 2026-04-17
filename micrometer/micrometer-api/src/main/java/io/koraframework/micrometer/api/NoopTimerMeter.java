package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Tags;

import java.util.function.Supplier;

public final class NoopTimerMeter implements TimerMeter {

    public static final NoopTimerMeter INSTANCE = new NoopTimerMeter();

    private NoopTimerMeter() {}

    @Override
    public void recordElapsedFromNanos(long startedInNanos, Supplier<Tags> metricCacheKeyTags) {

    }

    @Override
    public void recordElapsedFromNanos(long startedInNanos, Tags metricCacheKeyTags) {

    }
}

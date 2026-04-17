package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Tags;

import java.util.function.Supplier;

public interface TimerMeter {

    void recordElapsedFromNanos(long startedInNanos, Supplier<Tags> metricCacheKeyTags);

    void recordElapsedFromNanos(long startedInNanos, Tags metricCacheKeyTags);
}

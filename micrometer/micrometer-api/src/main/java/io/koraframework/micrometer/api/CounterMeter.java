package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Tags;

import java.util.function.Supplier;

public interface CounterMeter {

    void recordIncrement(long increment, Supplier<Tags> metricCacheKeyTags);

    void recordIncrement(long increment, Tags metricCacheKeyTags);
}

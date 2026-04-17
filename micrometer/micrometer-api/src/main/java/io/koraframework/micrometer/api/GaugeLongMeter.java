package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Tags;

import java.util.function.Supplier;

public interface GaugeLongMeter {

    void recordValue(long value, Supplier<Tags> metricCacheKeyTags);

    void recordValue(long value, Tags metricCacheKeyTags);
}

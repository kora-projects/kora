package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Tags;

import java.util.function.Supplier;

public final class NoopGaugeLongMeter implements GaugeLongMeter {

    public static final NoopGaugeLongMeter INSTANCE = new NoopGaugeLongMeter();

    private NoopGaugeLongMeter() {}

    @Override
    public void recordValue(long value, Supplier<Tags> metricCacheKeyTags) {

    }

    @Override
    public void recordValue(long value, Tags metricCacheKeyTags) {

    }
}

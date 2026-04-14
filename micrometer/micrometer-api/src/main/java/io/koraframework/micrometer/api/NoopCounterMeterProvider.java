package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.noop.NoopCounter;

public final class NoopCounterMeterProvider implements Meter.MeterProvider<Counter> {

    private static final Meter.Id EMPTY_COUNTER_ID = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.COUNTER);
    private static final NoopCounter NOOP_COUNTER = new NoopCounter(EMPTY_COUNTER_ID);

    public static final NoopCounterMeterProvider INSTANCE = new NoopCounterMeterProvider();

    private NoopCounterMeterProvider() { }

    @Override
    public Counter withTags(Iterable<? extends Tag> tags) {
        return NOOP_COUNTER;
    }

    @Override
    public String toString() {
        return "NoopCounterMeterBuilder{}";
    }
}

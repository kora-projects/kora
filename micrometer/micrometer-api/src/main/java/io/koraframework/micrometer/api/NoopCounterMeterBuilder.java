package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.noop.NoopCounter;
import org.jspecify.annotations.Nullable;

public final class NoopCounterMeterBuilder implements MeterBuilder<Counter> {

    private static final Meter.Id EMPTY_COUNTER_ID = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.COUNTER);
    private static final NoopCounter NOOP_COUNTER = new NoopCounter(EMPTY_COUNTER_ID);
    private static final MeterProvider<Counter> PROVIDER = _ -> NOOP_COUNTER;

    public static final NoopCounterMeterBuilder INSTANCE = new NoopCounterMeterBuilder();

    private NoopCounterMeterBuilder() { }

    @Override
    public MeterBuilder<Counter> tags(@Nullable String... tags) {
        return this;
    }

    @Override
    public MeterBuilder<Counter> tags(@Nullable Tag... tags) {
        return this;
    }

    @Override
    public MeterBuilder<Counter> tags(@Nullable Iterable<Tag> tags) {
        return this;
    }

    @Override
    public MeterBuilder<Counter> tag(@Nullable String key, @Nullable String value) {
        return this;
    }

    @Override
    public Tags getTags() {
        return Tags.empty();
    }

    @Override
    public Counter build() {
        return PROVIDER.get(Tags.empty());
    }

    @Override
    public String toString() {
        return "NoopCounterMeterBuilder{}";
    }
}

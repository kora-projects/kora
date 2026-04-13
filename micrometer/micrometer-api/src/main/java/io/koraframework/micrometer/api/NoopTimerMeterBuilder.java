package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.micrometer.core.instrument.noop.NoopTimer;
import org.jspecify.annotations.Nullable;

public final class NoopTimerMeterBuilder implements MeterBuilder<Timer> {

    private static final Meter.Id EMPTY_TIMER_ID = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.TIMER);
    private static final NoopTimer NOOP_TIMER = new NoopTimer(EMPTY_TIMER_ID);
    private static final MeterProvider<Timer> PROVIDER = _ -> NOOP_TIMER;

    public static final NoopTimerMeterBuilder INSTANCE = new NoopTimerMeterBuilder();

    private NoopTimerMeterBuilder() { }

    @Override
    public MeterBuilder<Timer> tags(@Nullable String... tags) {
        return this;
    }

    @Override
    public MeterBuilder<Timer> tags(@Nullable Tag... tags) {
        return this;
    }

    @Override
    public MeterBuilder<Timer> tags(@Nullable Iterable<Tag> tags) {
        return this;
    }

    @Override
    public MeterBuilder<Timer> tag(@Nullable String key, @Nullable String value) {
        return this;
    }

    @Override
    public Tags getTags() {
        return Tags.empty();
    }

    @Override
    public Timer build() {
        return PROVIDER.get(Tags.empty());
    }

    @Override
    public String toString() {
        return "NoopTimerMeterBuilder{}";
    }
}

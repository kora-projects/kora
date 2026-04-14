package io.koraframework.micrometer.api;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;

public final class NoopTimerMeterProvider implements Meter.MeterProvider<Timer> {

    private static final Meter.Id EMPTY_TIMER_ID = new Meter.Id("empty", Tags.empty(), null, null, Meter.Type.TIMER);
    private static final NoopTimer NOOP_TIMER = new NoopTimer(EMPTY_TIMER_ID);

    public static final NoopTimerMeterProvider INSTANCE = new NoopTimerMeterProvider();

    private NoopTimerMeterProvider() { }

    @Override
    public Timer withTags(Iterable<? extends Tag> tags) {
        return NOOP_TIMER;
    }

    @Override
    public String toString() {
        return "NoopTimerMeterBuilder{}";
    }
}

package ru.tinkoff.kora.common.telemetry;

import io.opentelemetry.api.trace.Span;

public interface Observation {
    ScopedValue<Observation> VALUE = ScopedValue.newInstance();

    Span span();

    static <T extends Observation> T current(Class<T> clazz) {
        return clazz.cast(VALUE.get());
    }

    void end();

    void observeError(Throwable e);
}

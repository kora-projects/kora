package ru.tinkoff.kora.common.telemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

public interface Observation {
    ScopedValue<Observation> VALUE = ScopedValue.newInstance();

    Span span();

    static <T extends Observation> T current(Class<T> clazz) {
        return clazz.cast(VALUE.get());
    }

    void end();

    void observeError(Throwable e);

    static ScopedValue.Carrier scoped(Observation observation) {
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, observation.span().getSpanContext().isValid() ? Context.current().with(observation.span()) : Context.current());
    }
}

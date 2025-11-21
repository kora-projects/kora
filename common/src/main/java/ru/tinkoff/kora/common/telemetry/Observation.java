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

    default ScopedValue.Carrier scoped() {
        return ScopedValue
            .where(Observation.VALUE, this)
            .where(OpentelemetryContext.VALUE, this.span().getSpanContext().isValid() ? Context.current().with(this.span()) : Context.current());
    }
}

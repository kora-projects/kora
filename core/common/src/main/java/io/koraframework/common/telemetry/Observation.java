package io.koraframework.common.telemetry;

import io.koraframework.common.context.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import org.jspecify.annotations.*;

public interface Observation {

    KoraContext.Key<Observation> OBSERVATION_KEY = KoraContext.Key.create();

    Span span();

    static <T extends Observation> @Nullable T current(Class<T> clazz) {
        KoraContext current = KoraContext.current();
        if (current == null) {
            return null;
        }
        Observation obs = current.get(OBSERVATION_KEY);
        return obs != null ? clazz.cast(obs) : null;
    }

    void end();

    void observeError(Throwable e);

    static KoraContext attach(KoraContext current, Observation observation) {
        Span span = observation.span();
        if (!span.getSpanContext().isValid()) {
            if (current.get(OBSERVATION_KEY) == null) {
                return current;
            }
            return current.with(OBSERVATION_KEY, observation);
        }

        var updated = current.with(OBSERVATION_KEY, observation);
        Context otelCtx = current.get(OpentelemetryContextStorage.OPENTELEMETRY_KEY);
        if (otelCtx == null) {
            otelCtx = Context.current();
        }
        return updated.with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, otelCtx.with(span));
    }

    static void detach(@Nullable KoraContext current, @Nullable Observation prevObs, @Nullable Context prevOtel) {
        if (current == null) {
            return;
        }
        if (prevObs != null || current.get(OBSERVATION_KEY) != null) {
            current.with(OBSERVATION_KEY, prevObs);
        }
        if (prevOtel != null || current.get(OpentelemetryContextStorage.OPENTELEMETRY_KEY) != null) {
            current.with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, prevOtel);
        }
    }
}

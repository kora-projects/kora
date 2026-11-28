package io.koraframework.common.telemetry;

import io.koraframework.common.context.*;
import io.opentelemetry.context.*;
import org.jspecify.annotations.*;

public class OpentelemetryContextStorage implements ContextStorage {

    public static final KoraContext.Key<Context> OPENTELEMETRY_KEY = KoraContext.Key.create();

    @Override
    public Scope attach(Context toAttach) {
        throw new UnsupportedOperationException("Kora uses static compile-time context pipelines, manual attach is disabled");
    }

    @Override
    @Nullable
    public Context current() {
        var current = KoraContext.current();
        if (current == null) {
            return null;
        }
        return current.get(OPENTELEMETRY_KEY);
    }

    @Override
    public Context root() {
        return ContextStorage.super.root();
    }
}

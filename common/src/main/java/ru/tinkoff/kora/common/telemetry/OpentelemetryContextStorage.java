package ru.tinkoff.kora.common.telemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import org.jspecify.annotations.Nullable;

public class OpentelemetryContextStorage implements ContextStorage {

    @Override
    public Scope attach(Context toAttach) {
        throw new IllegalStateException();
    }

    @Override
    @Nullable
    public Context current() {
        if (OpentelemetryContext.VALUE.isBound()) {
            return OpentelemetryContext.VALUE.get();
        }
        return null;
    }

    @Override
    public Context root() {
        return new OpentelemetryContext(ContextStorage.super.root());
    }
}

package ru.tinkoff.kora.common.telemetry;

import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;

public class OpentelemetryContextStorageProvider implements ContextStorageProvider {
    @Override
    public ContextStorage get() {
        return new OpentelemetryContextStorage();
    }
}

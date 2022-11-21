package ru.tinkoff.kora.micrometer.module;

import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.function.Function;

public interface PrometheusMeterRegistryInitializer extends Function<PrometheusMeterRegistry, PrometheusMeterRegistry> {
}

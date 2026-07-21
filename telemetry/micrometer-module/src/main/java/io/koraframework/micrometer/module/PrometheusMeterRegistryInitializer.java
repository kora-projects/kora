package io.koraframework.micrometer.module;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.util.function.Function;

@FunctionalInterface
public interface PrometheusMeterRegistryInitializer extends Function<PrometheusMeterRegistry, PrometheusMeterRegistry> {

}

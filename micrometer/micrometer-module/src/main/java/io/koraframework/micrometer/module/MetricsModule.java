package io.koraframework.micrometer.module;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.contrib.metrics.micrometer.CallbackRegistrar;
import io.opentelemetry.contrib.metrics.micrometer.MicrometerMeterProvider;
import org.jspecify.annotations.Nullable;
import io.koraframework.application.graph.All;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.annotation.Root;

public interface MetricsModule {
    @Root
    @DefaultComponent
    default PrometheusMeterRegistryWrapper prometheusMeterRegistry(All<PrometheusMeterRegistryInitializer> initializers) {
        return new PrometheusMeterRegistryWrapper(initializers);
    }

    @DefaultComponent
    default MicrometerMeterProvider micrometerMeterProvider(MeterRegistry registry, @Nullable CallbackRegistrar callbackRegistrar) {
        return MicrometerMeterProvider.builder(registry)
            .setCallbackRegistrar(callbackRegistrar)
            .build();
    }
}

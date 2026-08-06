package io.koraframework.micrometer.module;

import io.koraframework.application.graph.Wrapped;
import io.koraframework.telemetry.common.MetricsScraper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.contrib.metrics.micrometer.CallbackRegistrar;
import io.opentelemetry.contrib.metrics.micrometer.MicrometerMeterProvider;
import org.jspecify.annotations.Nullable;
import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;

public interface MetricsModule {

    @Root
    @DefaultComponent
    default Wrapped<MeterRegistry> prometheusMeterRegistry(All<PrometheusMeterRegistryInitializer> initializers) {
        return new PrometheusMeterRegistryWrapper(initializers);
    }

    @DefaultComponent
    default MetricsScraper prometheusMetricsScraper(MeterRegistry registry) {
        if (registry instanceof PrometheusMeterRegistry prometheus) {
            return prometheus::scrape;
        }

        // a registry that is not Prometheus cannot be scraped in this format; an application replacing it
        // provides its own MetricsScraper, which wins over this @DefaultComponent
        return os -> {};
    }

    @DefaultComponent
    default MicrometerMeterProvider micrometerMeterProvider(MeterRegistry registry, @Nullable CallbackRegistrar callbackRegistrar) {
        return MicrometerMeterProvider.builder(registry)
            .setCallbackRegistrar(callbackRegistrar)
            .build();
    }
}

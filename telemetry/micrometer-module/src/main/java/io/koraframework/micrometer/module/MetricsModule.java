package io.koraframework.micrometer.module;

import io.koraframework.application.graph.Wrapped;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.koraframework.telemetry.common.MetricsScraper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.contrib.metrics.micrometer.CallbackRegistrar;
import io.opentelemetry.contrib.metrics.micrometer.MicrometerMeterProvider;
import org.jspecify.annotations.Nullable;
import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;

import java.util.LinkedHashMap;

public interface MetricsModule {

    @DefaultComponent
    default MetricsConfig metricsConfig(Config config, ConfigValueMapper<MetricsConfig> mapper) {
        return mapper.mapOrThrow(config.get("metrics"));
    }

    @Root
    @DefaultComponent
    default Wrapped<MeterRegistry> prometheusMeterRegistry(MetricsConfig config, All<PrometheusMeterRegistryInitializer> initializers) {
        if (!config.enabled()) {
            return () -> NoopMeterRegistry.INSTANCE;
        }
        return new PrometheusMeterRegistryWrapper(initializers);
    }

    /**
     * Global component that modifies all metrics: registers a {@link MeterFilter} adding common tags collected from
     * {@link MetricsConfig#tags()} and every {@link MetricsTagsProvider} bean to every meter in the registry.
     */
    @DefaultComponent
    default PrometheusMeterRegistryInitializer commonTagsMeterRegistryInitializer(MetricsConfig config, All<MetricsTagsProvider> tagsProviders) {
        var merged = new LinkedHashMap<String, String>();
        for (var provider : tagsProviders) {
            merged.putAll(provider.tags());
        }
        // config tags are applied last so static configuration wins on key conflicts
        merged.putAll(config.tags());
        if (merged.isEmpty()) {
            return registry -> registry;
        }
        var tags = Tags.of(merged.entrySet().stream()
            .map(e -> Tag.of(e.getKey(), e.getValue()))
            .toList());
        return registry -> {
            registry.config().meterFilter(MeterFilter.commonTags(tags));
            return registry;
        };
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

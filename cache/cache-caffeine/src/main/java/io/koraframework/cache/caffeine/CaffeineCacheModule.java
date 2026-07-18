package io.koraframework.cache.caffeine;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory;
import io.koraframework.cache.caffeine.telemetry.impl.DefaultCaffeineCacheLoggerFactory;
import io.koraframework.cache.caffeine.telemetry.impl.DefaultCaffeineCacheMetricsFactory;
import io.koraframework.cache.caffeine.telemetry.impl.DefaultCaffeineCacheTelemetryFactory;
import io.koraframework.common.annotation.DefaultComponent;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface CaffeineCacheModule {

    @DefaultComponent
    default CaffeineCacheFactory caffeineCacheFactory(@Nullable MeterRegistry meterRegistry) {
        return new CaffeineFactory(meterRegistry);
    }

    @DefaultComponent
    default CaffeineCacheTelemetryFactory defaultCaffeineCacheTelemetryFactory(@Nullable Tracer tracer,
                                                                              @Nullable MeterRegistry meterRegistry,
                                                                              @Nullable DefaultCaffeineCacheLoggerFactory loggerFactory,
                                                                              @Nullable DefaultCaffeineCacheMetricsFactory metricsFactory) {
        return new DefaultCaffeineCacheTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}

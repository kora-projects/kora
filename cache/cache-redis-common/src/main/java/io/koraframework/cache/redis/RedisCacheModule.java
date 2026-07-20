package io.koraframework.cache.redis;

import io.koraframework.cache.CacheCommonModule;
import io.koraframework.cache.redis.mapper.RedisCacheMapperModule;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryFactory;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheLoggerFactory;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheMetricsFactory;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheTelemetryFactory;
import io.koraframework.common.annotation.DefaultComponent;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface RedisCacheModule extends RedisCacheMapperModule, CacheCommonModule {

    @DefaultComponent
    default RedisCacheTelemetryFactory defaultRedisCacheTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultRedisCacheLoggerFactory loggerFactory,
                                                                         @Nullable DefaultRedisCacheMetricsFactory metricsFactory) {
        return new DefaultRedisCacheTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}

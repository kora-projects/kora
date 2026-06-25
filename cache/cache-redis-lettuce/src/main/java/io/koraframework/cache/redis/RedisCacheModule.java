package io.koraframework.cache.redis;

import io.koraframework.cache.redis.lettuce.LettuceCacheModule;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryFactory;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheLoggerFactory;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheMetricsFactory;
import io.koraframework.cache.redis.telemetry.impl.DefaultRedisCacheTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface RedisCacheModule extends RedisCacheMapperModule, LettuceCacheModule {

    @DefaultComponent
    default RedisCacheTelemetryFactory redisCacheTelemetryFactory(@Nullable Tracer tracer,
                                                                  @Nullable MeterRegistry meterRegistry,
                                                                  @Nullable DefaultRedisCacheLoggerFactory loggerFactory,
                                                                  @Nullable DefaultRedisCacheMetricsFactory metricsFactory) {
        return new DefaultRedisCacheTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}

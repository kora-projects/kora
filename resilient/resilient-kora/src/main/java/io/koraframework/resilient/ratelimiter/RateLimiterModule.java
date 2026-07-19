package io.koraframework.resilient.ratelimiter;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetryFactory;
import io.koraframework.resilient.ratelimiter.telemetry.impl.DefaultRateLimiterLoggerFactory;
import io.koraframework.resilient.ratelimiter.telemetry.impl.DefaultRateLimiterMetricsFactory;
import io.koraframework.resilient.ratelimiter.telemetry.impl.DefaultRateLimiterTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface RateLimiterModule {

    @DefaultComponent
    default RateLimiterTelemetryFactory defaultRateLimiterTelemetryFactory(@Nullable Tracer tracer,
                                                                           @Nullable MeterRegistry meterRegistry,
                                                                           @Nullable DefaultRateLimiterLoggerFactory loggerFactory,
                                                                           @Nullable DefaultRateLimiterMetricsFactory metricsFactory) {
        return new DefaultRateLimiterTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}

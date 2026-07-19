package io.koraframework.resilient;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig;
import io.koraframework.resilient.fallback.telemetry.FallbackTelemetryConfig;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetryConfig;
import io.koraframework.resilient.retry.telemetry.RetryTelemetryConfig;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetryConfig;

@ConfigMapper
public interface ResilientConfig {

    CircuitBreakerTelemetryConfig circuitBreaker();

    RetryTelemetryConfig retry();

    TimeoutTelemetryConfig timeout();

    FallbackTelemetryConfig fallback();

    RateLimiterTelemetryConfig rateLimiter();
}

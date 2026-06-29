package io.koraframework.resilient.ratelimiter.telemetry;

public interface RateLimiterTelemetryFactory {

    RateLimiterTelemetry get(String name, RateLimiterTelemetryConfig config);
}

package io.koraframework.resilient;

import io.koraframework.resilient.circuitbreaker.CircuitBreakerModule;
import io.koraframework.resilient.fallback.FallbackModule;
import io.koraframework.resilient.ratelimiter.RateLimiterModule;
import io.koraframework.resilient.retry.RetryModule;
import io.koraframework.resilient.timeout.TimeoutModule;

public interface ResilientModule extends CircuitBreakerModule, RetryModule, TimeoutModule, FallbackModule, RateLimiterModule {

}

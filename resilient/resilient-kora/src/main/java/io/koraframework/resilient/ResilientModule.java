package io.koraframework.resilient;

import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.resilient.circuitbreaker.CircuitBreakerModule;
import io.koraframework.resilient.fallback.FallbackModule;
import io.koraframework.resilient.ratelimiter.RateLimiterModule;
import io.koraframework.resilient.retry.RetryModule;
import io.koraframework.resilient.timeout.TimeoutModule;

public interface ResilientModule extends CircuitBreakerModule, RetryModule, TimeoutModule, FallbackModule, RateLimiterModule {

    default ResilientConfig koraResilientConfig(Config config, ConfigValueMapper<ResilientConfig> mapper) {
        return mapper.mapOrThrow(config.get("resilient.telemetry"));
    }
}

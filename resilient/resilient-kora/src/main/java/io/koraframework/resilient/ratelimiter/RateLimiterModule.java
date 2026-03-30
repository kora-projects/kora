package io.koraframework.resilient.ratelimiter;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

public interface RateLimiterModule {

    default RateLimiterConfig koraRateLimiterConfig(Config config, ConfigValueExtractor<RateLimiterConfig> extractor) {
        var resilient = config.get("resilient");
        return extractor.extract(resilient);
    }

    default RateLimiterManager koraRateLimiterManager(RateLimiterConfig config,
                                                      @Nullable RateLimiterMetrics metrics) {
        return new KoraRateLimiterManager(config,
            (metrics == null)
                ? new NoopRateLimiterMetrics()
                : metrics);
    }
}

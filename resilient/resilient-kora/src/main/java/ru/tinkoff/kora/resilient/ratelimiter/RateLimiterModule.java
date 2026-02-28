package ru.tinkoff.kora.resilient.ratelimiter;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

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

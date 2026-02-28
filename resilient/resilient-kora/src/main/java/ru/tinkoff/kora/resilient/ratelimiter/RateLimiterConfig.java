package ru.tinkoff.kora.resilient.ratelimiter;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface RateLimiterConfig {

    String DEFAULT = "default";

    default Map<String, NamedConfig> ratelimiter() {
        return Map.of();
    }

    /**
     * {@link #limitForPeriod} Configures the number of permissions available during one limit refresh period.
     * Must be greater than 0.<br>
     * {@link #limitRefreshPeriod} Configures the period of a limit refresh. After each period the rate limiter
     * sets its permissions count to the {@link #limitForPeriod} value. Must be greater than 0.<br>
     */
    @ConfigValueExtractor
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        /**
         * @return number of permissions available per {@link #limitRefreshPeriod()}
         */
        @Nullable
        Integer limitForPeriod();

        /**
         * @return the period after which permissions are refreshed
         */
        @Nullable
        Duration limitRefreshPeriod();
    }

    default NamedConfig getNamedConfig(String name) {
        final NamedConfig defaultConfig = ratelimiter().get(DEFAULT);
        final NamedConfig namedConfig = ratelimiter().getOrDefault(name, defaultConfig);
        if (namedConfig == null) {
            throw new IllegalStateException("RateLimiter no configuration is provided, but either '%s' or '%s' config is required".formatted(name, DEFAULT));
        }

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.limitForPeriod() == null) {
            throw new IllegalStateException("RateLimiter property '%s' is not configured in either '%s' or '%s' config"
                .formatted("limitForPeriod", name, DEFAULT));
        }
        if (mergedConfig.limitRefreshPeriod() == null) {
            throw new IllegalStateException("RateLimiter property '%s' is not configured in either '%s' or '%s' config"
                .formatted("limitRefreshPeriod", name, DEFAULT));
        }
        if (mergedConfig.limitForPeriod() < 1) {
            throw new IllegalArgumentException("RateLimiter '%s' property '%s' must be greater than 0, but was: %s"
                .formatted(name, "limitForPeriod", mergedConfig.limitForPeriod()));
        }
        if (mergedConfig.limitRefreshPeriod().isNegative() || mergedConfig.limitRefreshPeriod().isZero()) {
            throw new IllegalArgumentException("RateLimiter '%s' property '%s' must be positive, but was: %s"
                .formatted(name, "limitRefreshPeriod", mergedConfig.limitRefreshPeriod()));
        }

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new $RateLimiterConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.limitForPeriod() == null ? defaultConfig.limitForPeriod() : namedConfig.limitForPeriod(),
            namedConfig.limitRefreshPeriod() == null ? defaultConfig.limitRefreshPeriod() : namedConfig.limitRefreshPeriod()
        );
    }
}

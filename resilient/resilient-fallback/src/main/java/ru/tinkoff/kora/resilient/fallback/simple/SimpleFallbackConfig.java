package ru.tinkoff.kora.resilient.fallback.simple;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;

import javax.annotation.Nonnull;
import java.util.Map;

@ConfigValueExtractor
public interface SimpleFallbackConfig {
    String DEFAULT = "default";
    NamedConfig DEFAULT_CONFIG = new $SimpleFallbackConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Defaults();

    default Map<String, NamedConfig> fallback() {
        return Map.of();
    }

    default NamedConfig getNamedConfig(@Nonnull String name) {
        final NamedConfig defaultConfig = fallback().getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedConfig namedConfig = fallback().getOrDefault(name, defaultConfig);
        return merge(namedConfig, defaultConfig);
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new $SimpleFallbackConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName());
    }

    /**
     * {@link #failurePredicateName} {@link FallbackFailurePredicate#name()} default is {@link SimpleFallbackFailurePredicate}
     */
    @ConfigValueExtractor
    interface NamedConfig {
        default String failurePredicateName() {
            return SimpleFallbackFailurePredicate.class.getCanonicalName();
        }
    }
}

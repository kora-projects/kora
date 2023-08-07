package ru.tinkoff.kora.resilient.fallback;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nonnull;
import java.util.Map;

@ConfigValueExtractor
public interface FallbackConfig {
    String DEFAULT = "default";
    NamedConfig DEFAULT_CONFIG = new $FallbackConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Defaults();

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

        return new $FallbackConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName());
    }

    /**
     * {@link #failurePredicateName} {@link FallbackPredicate#name()} default is {@link KoraFallbackPredicate}
     */
    @ConfigValueExtractor
    interface NamedConfig {
        default String failurePredicateName() {
            return KoraFallbackPredicate.class.getCanonicalName();
        }
    }
}

package ru.tinkoff.kora.resilient.fallback;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.util.Map;

@ConfigValueExtractor
public interface FallbackConfig {

    String DEFAULT = "default";

    NamedConfig DEFAULT_CONFIG = new $FallbackConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Defaults();

    default Map<String, NamedConfig> fallback() {
        return Map.of();
    }

    /**
     * {@link #failurePredicateName} {@link FallbackPredicate#name()} default is {@link KoraFallbackPredicate}
     */
    @ConfigValueExtractor
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        default String failurePredicateName() {
            return KoraFallbackPredicate.class.getCanonicalName();
        }
    }

    default NamedConfig getNamedConfig(String name) {
        final NamedConfig defaultConfig = fallback().getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedConfig namedConfig = fallback().getOrDefault(name, defaultConfig);
        return merge(namedConfig, defaultConfig);
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new $FallbackConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName());
    }
}

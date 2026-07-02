package io.koraframework.resilient.fallback;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.resilient.fallback.telemetry.FallbackTelemetryConfig;

import java.util.Map;

@ConfigMapper
public interface FallbackConfig {

    String DEFAULT = "default";

    NamedConfig DEFAULT_CONFIG = new $FallbackConfig_NamedConfig_ConfigValueMapper.NamedConfig_Defaults();

    default Map<String, NamedConfig> fallback() {
        return Map.of();
    }

    FallbackTelemetryConfig telemetry();

    /**
     * {@link #failurePredicateName} {@link FallbackPredicate#name()} default is {@link KoraFallbackPredicate}
     */
    @ConfigMapper
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

        return new $FallbackConfig_NamedConfig_ConfigValueMapper.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName());
    }
}

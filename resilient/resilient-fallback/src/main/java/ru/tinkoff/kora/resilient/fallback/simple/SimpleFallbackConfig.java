package ru.tinkoff.kora.resilient.fallback.simple;

import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public record SimpleFallbackConfig(Map<String, NamedConfig> fallback) {

    public static final String DEFAULT = "default";

    private static final NamedConfig DEFAULT_CONFIG = new NamedConfig(SimpleFallbackFailurePredicate.class.getCanonicalName());

    public NamedConfig getNamedConfig(@Nonnull String name) {
        final NamedConfig defaultConfig = fallback.getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedConfig namedConfig = fallback.getOrDefault(name, defaultConfig);
        return merge(namedConfig, defaultConfig);
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new NamedConfig(namedConfig.failurePredicateName == null ? defaultConfig.failurePredicateName : namedConfig.failurePredicateName);
    }

    /**
     * {@link #failurePredicateName} {@link FallbackFailurePredicate#name()} default is {@link SimpleFallbackFailurePredicate}
     */
    public record NamedConfig(@Nullable String failurePredicateName) {

        public NamedConfig(@Nullable String failurePredicateName) {
            this.failurePredicateName = (failurePredicateName == null) ? SimpleFallbackFailurePredicate.class.getCanonicalName() : failurePredicateName;
        }
    }
}

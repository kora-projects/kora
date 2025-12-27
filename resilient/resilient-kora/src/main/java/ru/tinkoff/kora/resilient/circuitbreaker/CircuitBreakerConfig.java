package ru.tinkoff.kora.resilient.circuitbreaker;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface CircuitBreakerConfig {

    String DEFAULT = "default";

    default Map<String, NamedConfig> circuitbreaker() {
        return Map.of();
    }

    /**
     * You can use <a href="https://resilience4j.readme.io/docs/circuitbreaker">Resilient4j documentation</a> as a description of how CircuitBreaker works and how similar properties are configution its parts
     * <p>
     * {@link #failureRateThreshold} Configures the failure rate threshold in percentage. If the failure rate is equal to or greater than the threshold, the CircuitBreaker transitions to open and starts short-circuiting calls. The threshold must be greater than 0 and not greater than 100.<br>
     * {@link #waitDurationInOpenState} Configures an interval function with a fixed wait duration which controls how long the CircuitBreaker should stay open, before it switches to half open.<br>
     * {@link #permittedCallsInHalfOpenState} Configures the number of permitted calls that must succeed when the CircuitBreaker is half open.<br>
     * {@link #slidingWindowSize} Configures the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed.<br>
     * {@link #minimumRequiredCalls} Configures the minimum number of calls which are required (per sliding window period) before the CircuitBreaker can calculate the error rate.<br>
     * {@link #failurePredicateName} {@link CircuitBreakerPredicate#name()} default is {@link KoraCircuitBreakerPredicate}<br>
     */
    @ConfigValueExtractor
    interface NamedConfig {

        @Nullable
        Boolean enabled();

        @Nullable
        Integer failureRateThreshold();

        @Nullable
        Duration waitDurationInOpenState();

        @Nullable
        Integer permittedCallsInHalfOpenState();

        @Nullable
        Long slidingWindowSize();

        @Nullable
        Long minimumRequiredCalls();

        default String failurePredicateName() {
            return KoraCircuitBreakerPredicate.class.getCanonicalName();
        }
    }

    default NamedConfig getNamedConfig(String name) {
        final NamedConfig defaultConfig = circuitbreaker().get(DEFAULT);
        final NamedConfig namedConfig = circuitbreaker().getOrDefault(name, defaultConfig);
        if (namedConfig == null)
            throw new IllegalStateException("CircuitBreaker no configuration is provided, but either '%s' or '%s' config is required".formatted(name, DEFAULT));

        final NamedConfig mergedConfig = merge(namedConfig, defaultConfig);
        if (mergedConfig.failureRateThreshold() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("failureRateThreshold", name, DEFAULT));
        if (mergedConfig.waitDurationInOpenState() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("waitDurationInOpenState", name, DEFAULT));
        if (mergedConfig.permittedCallsInHalfOpenState() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("permittedCallsInHalfOpenState", name, DEFAULT));
        if (mergedConfig.slidingWindowSize() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("slidingWindowSize", name, DEFAULT));
        if (mergedConfig.minimumRequiredCalls() == null)
            throw new IllegalStateException("CircuitBreaker property '%s' is not configured in either '%s' or '%s' config"
                .formatted("minimumRequiredCalls", name, DEFAULT));

        if (mergedConfig.minimumRequiredCalls() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "minimumRequiredCalls", mergedConfig.minimumRequiredCalls()));
        if (mergedConfig.slidingWindowSize() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "slidingWindowSize", mergedConfig.slidingWindowSize()));
        if (mergedConfig.permittedCallsInHalfOpenState() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(name, "permittedCallsInHalfOpenState", mergedConfig.slidingWindowSize()));
        if (mergedConfig.minimumRequiredCalls() > mergedConfig.slidingWindowSize())
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' has value %s, it can't be greater than property '%s' which value was: %s"
                .formatted(name, "minimumRequiredCalls", mergedConfig.minimumRequiredCalls(), "slidingWindowSize", mergedConfig.slidingWindowSize()));
        if (mergedConfig.failureRateThreshold() > 100 || mergedConfig.failureRateThreshold() < 1)
            throw new IllegalArgumentException("CircuitBreaker '%s' failureRateThreshold is percentage and must be in range from 1 to 100, but was: %s"
                .formatted(name, mergedConfig.failureRateThreshold()));

        return mergedConfig;
    }

    private static NamedConfig merge(NamedConfig namedConfig, NamedConfig defaultConfig) {
        if (defaultConfig == null) {
            return namedConfig;
        }

        return new $CircuitBreakerConfig_NamedConfig_ConfigValueExtractor.NamedConfig_Impl(
            namedConfig.enabled() != null ? Boolean.TRUE.equals(namedConfig.enabled()) : (defaultConfig.enabled() == null || Boolean.TRUE.equals(defaultConfig.enabled())),
            namedConfig.failureRateThreshold() == null ? defaultConfig.failureRateThreshold() : namedConfig.failureRateThreshold(),
            namedConfig.waitDurationInOpenState() == null ? defaultConfig.waitDurationInOpenState() : namedConfig.waitDurationInOpenState(),
            namedConfig.permittedCallsInHalfOpenState() == null ? defaultConfig.permittedCallsInHalfOpenState() : namedConfig.permittedCallsInHalfOpenState(),
            namedConfig.slidingWindowSize() == null ? defaultConfig.slidingWindowSize() : namedConfig.slidingWindowSize(),
            namedConfig.minimumRequiredCalls() == null ? defaultConfig.minimumRequiredCalls() : namedConfig.minimumRequiredCalls(),
            namedConfig.failurePredicateName() == null ? defaultConfig.failurePredicateName() : namedConfig.failurePredicateName()
        );
    }
}

package ru.tinkoff.kora.resilient.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraFallbackManager implements FallbackManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraFallbackManager.class);

    private final Map<String, Fallback> fallbackerMap = new ConcurrentHashMap<>();

    private final FallbackConfig configs;
    private final FallbackMetrics metrics;
    private final List<FallbackPredicate> failurePredicates;

    KoraFallbackManager(FallbackConfig configs, List<FallbackPredicate> failurePredicates, FallbackMetrics metrics) {
        this.configs = configs;
        this.metrics = metrics;
        this.failurePredicates = failurePredicates;
    }

    @Nonnull
    @Override
    public Fallback get(@Nonnull String name) {
        return fallbackerMap.computeIfAbsent(name, k -> {
            final FallbackConfig.NamedConfig config = configs.getNamedConfig(name);
            final FallbackPredicate failurePredicate = getFailurePredicate(config);
            logger.debug("Creating Fallback named '{}' with failure predicate '{}' and config {}",
                name, failurePredicate.name(), config);

            return new KoraFallback(name, metrics, failurePredicate);
        });
    }

    private FallbackPredicate getFailurePredicate(FallbackConfig.NamedConfig config) {
        return failurePredicates.stream()
            .filter(p -> p.name().equals(config.failurePredicateName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FailurePredicateClassName '" + config.failurePredicateName() + "' is not present as bean, please declare it as bean"));
    }
}

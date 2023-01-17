package ru.tinkoff.kora.resilient.fallback.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;
import ru.tinkoff.kora.resilient.fallback.Fallbacker;
import ru.tinkoff.kora.resilient.fallback.FallbackerManager;
import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleFallbackerManager implements FallbackerManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleFallbackerManager.class);

    private final Map<String, Fallbacker> fallbackerMap = new ConcurrentHashMap<>();

    private final SimpleFallbackConfig configs;
    private final FallbackMetrics metrics;
    private final List<FallbackFailurePredicate> failurePredicates;

    SimpleFallbackerManager(SimpleFallbackConfig configs, List<FallbackFailurePredicate> failurePredicates, FallbackMetrics metrics) {
        this.configs = configs;
        this.metrics = metrics;
        this.failurePredicates = failurePredicates;
    }

    @Nonnull
    @Override
    public Fallbacker get(@Nonnull String name) {
        return fallbackerMap.computeIfAbsent(name, k -> {
            final SimpleFallbackConfig.NamedConfig config = configs.getNamedConfig(name);
            final FallbackFailurePredicate failurePredicate = getFailurePredicate(config);
            logger.debug("Creating Fallbacker named '{}' with failure predicate '{}' and config {}",
                name, failurePredicate.name(), config);

            return new SimpleFallbacker(name, metrics, failurePredicate);
        });
    }

    private FallbackFailurePredicate getFailurePredicate(SimpleFallbackConfig.NamedConfig config) {
        return failurePredicates.stream()
            .filter(p -> p.name().equals(config.failurePredicateName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FailurePredicateClassName '" + config.failurePredicateName() + "' is not present as bean, please declare it as bean"));
    }
}

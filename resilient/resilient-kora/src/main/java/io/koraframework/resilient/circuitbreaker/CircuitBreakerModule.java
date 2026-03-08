package io.koraframework.resilient.circuitbreaker;

import org.jspecify.annotations.Nullable;
import io.koraframework.application.graph.All;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

public interface CircuitBreakerModule {
    default CircuitBreakerConfig koraCircuitBreakerConfig(Config config, ConfigValueExtractor<CircuitBreakerConfig> extractor) {
        var resilient = config.get("resilient");
        return extractor.extract(resilient);
    }

    default CircuitBreakerManager koraCircuitBreakerManager(CircuitBreakerConfig config,
                                                            All<CircuitBreakerPredicate> failurePredicates,
                                                            @Nullable CircuitBreakerMetrics metrics) {
        return new KoraCircuitBreakerManager(config, failurePredicates,
            (metrics == null)
                ? new NoopCircuitBreakerMetrics()
                : metrics);
    }

    default CircuitBreakerPredicate koraDefaultCircuitBreakerFailurePredicate() {
        return new KoraCircuitBreakerPredicate();
    }
}

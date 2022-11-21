package ru.tinkoff.kora.resilient.circuitbreaker.impl;

import com.typesafe.config.Config;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nullable;
import java.util.Map;

public interface FastCircuitBreakerModule {
    default ConfigValueExtractor<FastCircuitBreakerConfig> fastCircuitBreakerConfigValueExtractor(ConfigValueExtractor<Map<String, FastCircuitBreakerConfig.NamedConfig>> extractor) {
        return new ObjectConfigValueExtractor<>() {
            @Override
            protected FastCircuitBreakerConfig extract(Config config) {
                var fast = Map.<String, FastCircuitBreakerConfig.NamedConfig>of();
                if (config.hasPath("fast")) {
                    fast = extractor.extract(config.getValue("fast"));
                }
                return new FastCircuitBreakerConfig(fast);
            }
        };
    }


    default FastCircuitBreakerConfig fastCircuitBreakerConfig(Config config, ConfigValueExtractor<FastCircuitBreakerConfig> extractor) {
        return !config.hasPath("resilient.circuitBreaker")
            ? new FastCircuitBreakerConfig(Map.of())
            : extractor.extract(config.getValue("resilient.circuitBreaker"));
    }

    default CircuitBreakerManager fastCircuitBreakerManager(FastCircuitBreakerConfig config,
                                                            All<CircuitBreakerFailurePredicate> failurePredicates,
                                                            @Nullable CircuitBreakerMetrics metrics) {
        return new FastCircuitBreakerManager(config, failurePredicates,
            (metrics == null)
                ? NoopCircuitBreakerMetrics.INSTANCE
                : metrics);
    }

    default CircuitBreakerFailurePredicate fastDefaultCircuitBreakerFailurePredicate() {
        return new FastCircuitBreakerFailurePredicate();
    }
}

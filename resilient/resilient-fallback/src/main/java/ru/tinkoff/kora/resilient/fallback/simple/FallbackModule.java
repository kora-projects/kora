package ru.tinkoff.kora.resilient.fallback.simple;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;
import ru.tinkoff.kora.resilient.fallback.FallbackerManager;
import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nullable;

public interface FallbackModule {

    default SimpleFallbackConfig simpleFallbackConfig(Config config, ConfigValueExtractor<SimpleFallbackConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default FallbackerManager simpleFallbackManager(SimpleFallbackConfig config,
                                                    All<FallbackFailurePredicate> failurePredicates,
                                                    @Nullable FallbackMetrics metrics) {
        return new SimpleFallbackerManager(config, failurePredicates,
            (metrics == null)
                ? NoopFallbackMetrics.INSTANCE
                : metrics);
    }

    default FallbackFailurePredicate defaultFallbackFailurePredicate() {
        return new SimpleFallbackFailurePredicate();
    }
}

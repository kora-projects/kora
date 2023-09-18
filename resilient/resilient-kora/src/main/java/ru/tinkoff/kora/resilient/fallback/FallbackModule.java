package ru.tinkoff.kora.resilient.fallback;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

public interface FallbackModule {

    default FallbackConfig koraFallbackConfig(Config config, ConfigValueExtractor<FallbackConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default FallbackManager koraFallbackManager(FallbackConfig config,
                                                All<FallbackPredicate> failurePredicates,
                                                @Nullable FallbackMetrics metrics) {
        return new KoraFallbackManager(config, failurePredicates,
            (metrics == null)
                ? NoopFallbackMetrics.INSTANCE
                : metrics);
    }

    default FallbackPredicate defaultFallbackFailurePredicate() {
        return new KoraFallbackPredicate();
    }
}

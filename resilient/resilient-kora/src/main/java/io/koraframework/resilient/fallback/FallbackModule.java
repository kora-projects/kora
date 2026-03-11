package io.koraframework.resilient.fallback;

import org.jspecify.annotations.Nullable;
import io.koraframework.application.graph.All;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

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

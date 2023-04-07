package ru.tinkoff.kora.resilient.fallback.simple;

import com.typesafe.config.Config;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;
import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;
import ru.tinkoff.kora.resilient.fallback.FallbackerManager;
import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nullable;
import java.util.Map;

public interface FallbackModule {

    default ConfigValueExtractor<SimpleFallbackConfig> simpleFallbackConfigValueExtractor(ConfigValueExtractor<Map<String, SimpleFallbackConfig.NamedConfig>> extractor) {
        return new ObjectConfigValueExtractor<>() {
            @Override
            protected SimpleFallbackConfig extract(Config config) {
                var fast = Map.<String, SimpleFallbackConfig.NamedConfig>of();
                if (config.hasPath("fallback")) {
                    fast = extractor.extract(config.getValue("fallback"));
                }
                return new SimpleFallbackConfig(fast);
            }
        };
    }

    default SimpleFallbackConfig simpleFallbackConfig(Config config, ConfigValueExtractor<SimpleFallbackConfig> extractor) {
        return !config.hasPath("resilient")
            ? new SimpleFallbackConfig(Map.of())
            : extractor.extract(config.getValue("resilient"));
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

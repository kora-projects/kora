package io.koraframework.micrometer.module;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micrometer applies a {@link io.micrometer.core.instrument.config.MeterFilter} only to meters registered after it,
 * so common tags must be installed before any {@link PrometheusMeterRegistryInitializer} registers meters.
 */
class MetricsCommonTagsTest {

    private final MetricsModule module = new MetricsModule() {};

    @Test
    void commonTagsAppliedToInitializerMeters() throws Exception {
        var config = new MetricsConfig() {
            @Override
            public Map<String, String> tags() {
                return Map.of("app", "svc");
            }
        };
        MetricsTagsProvider provider = () -> Map.of("team", "core", "app", "overridden");
        PrometheusMeterRegistryInitializer initializer = registry -> {
            Counter.builder("user.counter").register(registry).increment();
            return registry;
        };

        var wrapped = module.prometheusMeterRegistry(config, new All.StaticAll<>(List.of(provider)), new All.StaticAll<>(List.of(initializer)));
        ((Lifecycle) wrapped).init();
        try {
            var out = new ByteArrayOutputStream();
            ((PrometheusMeterRegistry) wrapped.value()).scrape(out);
            var lines = out.toString(StandardCharsets.UTF_8).lines()
                .filter(l -> !l.startsWith("#") && !l.isBlank())
                .toList();

            assertThat(lines).anyMatch(l -> l.startsWith("user_counter_total"));
            assertThat(lines).allMatch(l -> l.contains("app=\"svc\"") && l.contains("team=\"core\""));
        } finally {
            ((Lifecycle) wrapped).release();
        }
    }
}

package io.koraframework.kora.app.annotation.processor;

import io.koraframework.application.graph.Wrapped;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsCommonTagsGraphTest extends AbstractKoraAppTest {

    @Test
    public void commonTagsReachMetersOfComponentInitializer() throws Exception {
        var draw = compile("""
            import io.koraframework.micrometer.module.*;
            import java.util.Map;

            @KoraApp
            public interface ExampleApplication extends MetricsModule {
                default MetricsConfig appMetricsConfig() {
                    return new MetricsConfig() {
                        @Override
                        public Map<String, String> tags() {
                            return Map.of("app", "svc");
                        }
                    };
                }
            }
            """, """
            import io.koraframework.micrometer.module.*;
            import io.micrometer.core.instrument.*;
            import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

            @Component
            public final class UserInitializer implements PrometheusMeterRegistryInitializer {
                @Override
                public PrometheusMeterRegistry apply(PrometheusMeterRegistry registry) {
                    Counter.builder("user.counter").register(registry).increment();
                    return registry;
                }
            }
            """);
        var graph = draw.init();
        try {
            PrometheusMeterRegistry registry = null;
            for (var node : draw.getNodes()) {
                if (graph.get(node) instanceof Wrapped<?> w && w.value() instanceof PrometheusMeterRegistry p) {
                    registry = p;
                }
            }
            assertThat(registry).isNotNull();
            var out = new ByteArrayOutputStream();
            registry.scrape(out);
            var lines = out.toString(StandardCharsets.UTF_8).lines()
                .filter(l -> l.startsWith("user_counter_total") || l.startsWith("kora_up"))
                .toList();
            assertThat(lines).hasSize(2).allMatch(l -> l.contains("app=\"svc\""));
        } finally {
            graph.release();
        }
    }
}

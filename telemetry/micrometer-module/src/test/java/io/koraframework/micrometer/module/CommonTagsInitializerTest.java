package io.koraframework.micrometer.module;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommonTagsInitializerTest extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import io.koraframework.micrometer.module.*;
            import java.util.Map;
            """;
    }

    @Test
    void commonTagsApplyAlongsideUserInitializer() throws Exception {
        compile(List.of(new KoraAppProcessor()), """
            @KoraApp
            public interface TestApp extends MetricsModule {
                default MetricsConfig testMetricsConfig() {
                    return new MetricsConfig() {
                        @Override
                        public Map<String, String> tags() {
                            return Map.of("app", "kora");
                        }
                    };
                }

                default PrometheusMeterRegistryInitializer userInitializer() {
                    return registry -> registry;
                }
            }
            """);
        compileResult.assertSuccess();

        try (var graph = loadGraph("TestApp")) {
            MeterRegistry registry = graph.<PrometheusMeterRegistryWrapper>findByType(PrometheusMeterRegistryWrapper.class).value();
            var counter = Counter.builder("kora.test.counter").register(registry);

            assertThat(counter.getId().getTag("app")).isEqualTo("kora");
        }
    }
}

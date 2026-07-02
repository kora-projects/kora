package io.koraframework.config.annotation.processor;


import org.junit.jupiter.api.Test;
import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValueMapperGeneratorExtensionTest extends AbstractAnnotationProcessorTest {
    @Test
    public void testExtensionAnnotatedRecord() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(io.koraframework.config.common.mapper.ConfigValueMapper<TestConfig> mapper) { return ""; }
            }
            """, """
            @io.koraframework.config.common.annotation.ConfigMapper
            public record TestConfig(String value){}
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("TestApp");
        assertThat(graph.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testExtensionAnnotatedInterface() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(io.koraframework.config.common.mapper.ConfigValueMapper<TestConfig> mapper) { return ""; }
            }
            """, """
            @io.koraframework.config.common.annotation.ConfigMapper
            public interface TestConfig {
              String value();
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("TestApp");
        assertThat(graph.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testExtensionAnnotatedPojo() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(io.koraframework.config.common.mapper.ConfigValueMapper<TestConfig> mapper) { return ""; }
            }
            """, """
            @io.koraframework.config.common.annotation.ConfigMapper
            public class TestConfig {
              private String value;
            
              public String getValue() {
                return this.value;
              }
            
              public void setValue(String value) {
                this.value = value;
              }
            
              @Override
              public boolean equals(Object obj) {
                return obj instanceof TestConfig that && java.util.Objects.equals(this.value, that.value);
              }
            
              public int hashCode() { return java.util.Objects.hashCode(value); }
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("TestApp");
        assertThat(graph.draw().getNodes()).hasSize(2);
    }
}

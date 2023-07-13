package ru.tinkoff.kora.config.annotation.processor;


import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;
import ru.tinkoff.kora.config.annotation.processor.processor.ConfigParserAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigExtractorGeneratorExtensionTest extends AbstractAnnotationProcessorTest {
    @Test
    public void testExtensionAnnotatedRecord() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig> extractor) { return ""; }
            }
            """, """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
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
              default String root(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig> extractor) { return ""; }
            }
            """, """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
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
              default String root(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig> extractor) { return ""; }
            }
            """, """
            @ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor
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

    @Test
    public void testExtensionNonAnnotatedRecord() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig> extractor) { return ""; }
            }
            """, """
            public record TestConfig(String value){}
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("TestApp");
        assertThat(graph.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testExtensionNonAnnotatedInterface() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig> extractor) { return ""; }
            }
            """, """
            public interface TestConfig {
              String value();
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("TestApp");
        assertThat(graph.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testExtensionNonAnnotatedPojo() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ConfigParserAnnotationProcessor()), """
            @KoraApp
            public interface TestApp {
              @Root
              default String root(ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor<TestConfig> extractor) { return ""; }
            }
            """, """
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

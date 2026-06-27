package io.koraframework.json.annotation.processor.extension;

import org.junit.jupiter.api.Test;
import io.koraframework.json.annotation.processor.AbstractJsonAnnotationProcessorTest;
import io.koraframework.json.annotation.processor.JsonAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;

import javax.tools.Diagnostic;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class JsonKoraExtensionTest extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void testReaderFromExtensionNotFoundForInterface() {
        compile(List.of(new KoraAppProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              interface TestInterface {}
            
              default String root0(io.koraframework.json.common.JsonReader<TestInterface> r) {return "";}
            
              @Root
              default Integer root1(String test) {return 42;}
            }
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.diagnostic()).anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
            && d.getMessage(Locale.US).contains("Required dependency type wasn't found in graph and can't be auto created: " +
            "io.koraframework.json.common.JsonReader<io.koraframework.json.annotation.processor.extension.packageForJsonKoraExtensionTest.testReaderFromExtensionNotFoundForInterface.TestApp.TestInterface>"));
    }

    @Test
    public void testReaderFoundForAnnotatedRecord() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @io.koraframework.json.common.annotation.Json
              record TestRecord() {}
            
              @Root
              default Integer root1(io.koraframework.json.common.JsonReader<TestRecord> r) {return 42;}
            }
            """);
        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testWriterFromExtensionNotFoundForInterface() {
        compile(List.of(new KoraAppProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              interface TestInterface {}
            
              @Root
              default String root(io.koraframework.json.common.JsonWriter<TestInterface> r) {return "";}
            }
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.diagnostic()).anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
            && d.getMessage(Locale.US).contains("Required dependency type wasn't found in graph and can't be auto created: " +
            "io.koraframework.json.common.JsonWriter<io.koraframework.json.annotation.processor.extension.packageForJsonKoraExtensionTest.testWriterFromExtensionNotFoundForInterface.TestApp.TestInterface>"));
    }

    @Test
    public void testWriterFoundForAnnotatedRecord() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @io.koraframework.json.common.annotation.Json
              record TestRecord() {}
            
              @Root
              default Integer root1(io.koraframework.json.common.JsonWriter<TestRecord> r) {return 42;}
            }
            """);
        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testReaderFromExtensionGeneratedForSealedInterface() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
            
              @JsonDiscriminatorField("type")
              @io.koraframework.json.common.annotation.Json
              sealed interface TestInterface {
                @io.koraframework.json.common.annotation.Json
                record Impl1(String value) implements TestInterface { }
                @io.koraframework.json.common.annotation.Json
                record Impl2(int value) implements TestInterface { }
              }
            
              default String root0(io.koraframework.json.common.JsonReader<TestInterface> r) { return ""; }
            
              @Root
              default Integer root1(String test) { return 42; }
            }
            """);

        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(5);
    }

    @Test
    public void testWriterFromExtensionGeneratedForSealedInterface() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
            
              @JsonDiscriminatorField("type")
              @io.koraframework.json.common.annotation.Json
              sealed interface TestInterface {
                @io.koraframework.json.common.annotation.Json
                record Impl1(String value) implements TestInterface { }
                @io.koraframework.json.common.annotation.Json
                record Impl2(int value) implements TestInterface { }
              }
            
              default String root0(io.koraframework.json.common.JsonWriter<TestInterface> r) { return ""; }
            
              @Root
              default Integer root1(String test) { return 42; }
            }
            """);

        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(5);
    }
}

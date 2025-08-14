package ru.tinkoff.kora.json.annotation.processor.extension;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.annotation.processor.AbstractJsonAnnotationProcessorTest;
import ru.tinkoff.kora.json.annotation.processor.JsonAnnotationProcessor;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import javax.tools.Diagnostic;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class JsonKoraExtensionTest extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void testReaderFromExtensionNotFoundForInterface() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              interface TestInterface {}
            
              default String root0(ru.tinkoff.kora.json.common.JsonReader<TestInterface> r) {return "";}
            
              @Root
              default Integer root1(String test) {return 42;}
            }
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.diagnostic()).anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
            && d.getMessage(Locale.US).contains("Required dependency type wasn't found and can't be auto created: " +
            "ru.tinkoff.kora.json.common.JsonReader<ru.tinkoff.kora.json.annotation.processor.extension.packageForJsonKoraExtensionTest.testReaderFromExtensionNotFoundForInterface.TestApp.TestInterface>"));
    }

    @Test
    public void testReaderFoundForAnnotatedRecord() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @ru.tinkoff.kora.json.common.annotation.Json
              record TestRecord() {}
            
              @Root
              default Integer root1(ru.tinkoff.kora.json.common.JsonReader<TestRecord> r) {return 42;}
            }
            """);
        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testWriterFromExtensionNotFoundForInterface() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              interface TestInterface {}
            
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestInterface> r) {return "";}
            }
            """);

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.diagnostic()).anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
            && d.getMessage(Locale.US).contains("Required dependency type wasn't found and can't be auto created: " +
            "ru.tinkoff.kora.json.common.JsonWriter<ru.tinkoff.kora.json.annotation.processor.extension.packageForJsonKoraExtensionTest.testWriterFromExtensionNotFoundForInterface.TestApp.TestInterface>"));
    }

    @Test
    public void testWriterFoundForAnnotatedRecord() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @ru.tinkoff.kora.json.common.annotation.Json
              record TestRecord() {}
            
              @Root
              default Integer root1(ru.tinkoff.kora.json.common.JsonWriter<TestRecord> r) {return 42;}
            }
            """);
        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(2);
    }

    @Test
    public void testReaderFromExtensionGeneratedForSealedInterface() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
            
              @JsonDiscriminatorField("type")
              @ru.tinkoff.kora.json.common.annotation.Json
              sealed interface TestInterface {
                @ru.tinkoff.kora.json.common.annotation.Json
                record Impl1(String value) implements TestInterface { }
                @ru.tinkoff.kora.json.common.annotation.Json
                record Impl2(int value) implements TestInterface { }
              }
            
              default String root0(ru.tinkoff.kora.json.common.JsonReader<TestInterface> r) { return ""; }
            
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
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
            
              @JsonDiscriminatorField("type")
              @ru.tinkoff.kora.json.common.annotation.Json
              sealed interface TestInterface {
                @ru.tinkoff.kora.json.common.annotation.Json
                record Impl1(String value) implements TestInterface { }
                @ru.tinkoff.kora.json.common.annotation.Json
                record Impl2(int value) implements TestInterface { }
              }
            
              default String root0(ru.tinkoff.kora.json.common.JsonWriter<TestInterface> r) { return ""; }
            
              @Root
              default Integer root1(String test) { return 42; }
            }
            """);

        compileResult.assertSuccess();
        var app = loadGraph("TestApp");
        assertThat(app.draw().getNodes()).hasSize(5);
    }
}

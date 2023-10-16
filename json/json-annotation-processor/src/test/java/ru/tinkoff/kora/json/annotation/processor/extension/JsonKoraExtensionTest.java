package ru.tinkoff.kora.json.annotation.processor.extension;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.annotation.processor.AbstractJsonAnnotationProcessorTest;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import javax.tools.Diagnostic;
import java.util.List;
import java.util.Locale;

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

        Assertions.assertThat(compileResult.isFailed()).isTrue();
        Assertions.assertThat(compileResult.diagnostic()).anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
            && d.getMessage(Locale.US).contains("Required dependency type was not found and can't be auto created: " +
            "ru.tinkoff.kora.json.common.JsonReader<ru.tinkoff.kora.json.annotation.processor.extension.packageForJsonKoraExtensionTest.testReaderFromExtensionNotFoundForInterface.TestApp.TestInterface>"));
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

        Assertions.assertThat(compileResult.isFailed()).isTrue();
        Assertions.assertThat(compileResult.diagnostic()).anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
            && d.getMessage(Locale.US).contains("Required dependency type was not found and can't be auto created: " +
            "ru.tinkoff.kora.json.common.JsonWriter<ru.tinkoff.kora.json.annotation.processor.extension.packageForJsonKoraExtensionTest.testWriterFromExtensionNotFoundForInterface.TestApp.TestInterface>"));
    }
}

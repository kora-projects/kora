package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testEnum() {
        compile("""
            @Json
            public enum TestEnum {
              VALUE1, VALUE2
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestEnum");
        mapper.verify(enumConstant("TestEnum", "VALUE1"), "\"VALUE1\"");
        mapper.verify(enumConstant("TestEnum", "VALUE2"), "\"VALUE2\"");
    }


    @Test
    public void testReaderFromExtension() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              enum TestEnum {
                VALUE1, VALUE2
              }
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestEnum")).isNotNull();
    }

    @Test
    public void testWriterFromExtension() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              enum TestEnum {
                VALUE1, VALUE2
              }
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestEnum")).isNotNull();
    }

    @Test
    public void testAnnotationProcessedReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @Json
              enum TestEnum {
                VALUE1, VALUE2
              }
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestEnum")).isNotNull();
    }

    @Test
    public void testAnnotationProcessedWriterFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              @Json
              enum TestEnum {
                VALUE1, VALUE2
              }
              
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestEnum")).isNotNull();
    }
}

package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumTest extends AbstractJsonAnnotationProcessorTest {
    JsonReader<String> stringReader = JsonParser::getValueAsString;
    JsonWriter<String> stringWriter = JsonGenerator::writeString;

    @Test
    public void testEnum() {
        compile("""
            @Json
            public enum TestEnum {
              VALUE1, VALUE2
            }
            """);

        compileResult.assertSuccess();

        var mapper = mapper("TestEnum", List.of(stringReader), List.of(stringWriter));
        mapper.verify(enumConstant("TestEnum", "VALUE1"), "\"VALUE1\"");
        mapper.verify(enumConstant("TestEnum", "VALUE2"), "\"VALUE2\"");
    }

    @Test
    public void testEnumWithCustomJsonValue() {
        compile("""
            @Json
            public enum TestEnum {
              VALUE1, VALUE2;
              
              @Json
              public int intValue() {
                return ordinal();
              }
            }
            """);

        compileResult.assertSuccess();
        JsonReader<Integer> intReader = JsonParser::getIntValue;
        JsonWriter<Integer> intWriter = JsonGenerator::writeNumber;

        var mapper = mapper("TestEnum", List.of(intReader), List.of(intWriter));
        mapper.verify(enumConstant("TestEnum", "VALUE1"), "0");
        mapper.verify(enumConstant("TestEnum", "VALUE2"), "1");
    }


    @Test
    public void testReaderFromExtension() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              enum TestEnum {
                VALUE1, VALUE2
              }
              
              default ru.tinkoff.kora.json.common.JsonReader<String> stringReader() { return com.fasterxml.jackson.core.JsonParser::getValueAsString; }
              default ru.tinkoff.kora.json.common.JsonWriter<String> stringWriter() { return com.fasterxml.jackson.core.JsonGenerator::writeString; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull();
    }

    @Test
    public void testWriterFromExtension() {
        compile(List.of(new KoraAppProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              enum TestEnum {
                VALUE1, VALUE2
              }

              default ru.tinkoff.kora.json.common.JsonReader<String> stringReader() { return com.fasterxml.jackson.core.JsonParser::getValueAsString; }
              default ru.tinkoff.kora.json.common.JsonWriter<String> stringWriter() { return com.fasterxml.jackson.core.JsonGenerator::writeString; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull();
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

              default ru.tinkoff.kora.json.common.JsonReader<String> stringReader() { return com.fasterxml.jackson.core.JsonParser::getValueAsString; }
              default ru.tinkoff.kora.json.common.JsonWriter<String> stringWriter() { return com.fasterxml.jackson.core.JsonGenerator::writeString; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull();
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

              default ru.tinkoff.kora.json.common.JsonReader<String> stringReader() { return com.fasterxml.jackson.core.JsonParser::getValueAsString; }
              default ru.tinkoff.kora.json.common.JsonWriter<String> stringWriter() { return com.fasterxml.jackson.core.JsonGenerator::writeString; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonWriter<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull();
    }
}

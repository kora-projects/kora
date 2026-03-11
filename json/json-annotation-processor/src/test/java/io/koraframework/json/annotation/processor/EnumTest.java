package io.koraframework.json.annotation.processor;

import org.junit.jupiter.api.Test;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

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
    public void testAnnotationProcessedReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @Json
              enum TestEnum {
                VALUE1, VALUE2
              }
            
              default io.koraframework.json.common.JsonReader<String> stringReader() { return tools.jackson.core.JsonParser::getValueAsString; }
              default io.koraframework.json.common.JsonWriter<String> stringWriter() { return tools.jackson.core.JsonGenerator::writeString; }
            
              @Root
              default String root(io.koraframework.json.common.JsonReader<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull();
    }

    @Test
    public void testAnnotationProcessedWriterFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @io.koraframework.common.KoraApp
            public interface TestApp {
              @Json
              enum TestEnum {
                VALUE1, VALUE2
              }
            
              default io.koraframework.json.common.JsonReader<String> stringReader() { return tools.jackson.core.JsonParser::getValueAsString; }
              default io.koraframework.json.common.JsonWriter<String> stringWriter() { return tools.jackson.core.JsonGenerator::writeString; }
            
              @Root
              default String root(io.koraframework.json.common.JsonWriter<TestEnum> r) {return "";}
            }
            """);

        compileResult.assertSuccess();
        assertThat(writer("TestApp_TestEnum", stringWriter)).isNotNull();
    }
}

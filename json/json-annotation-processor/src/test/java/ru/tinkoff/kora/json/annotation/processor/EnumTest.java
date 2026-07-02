package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.kora.app.annotation.processor.KoraAppProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Test
    public void testEnumReaderFromFactoryMethod() {
        compile("""
                @Json
                public enum TestEnum {
                  VALUE1("value1"), VALUE2("value2"), OTHER("other");
                
                  private final String value;
                  TestEnum(String value) { this.value = value; }
                
                  @JsonReader
                  public static TestEnum fromValue(String value) {
                    for (var v : values()) { if (v.value.equals(value)) return v; }
                    return OTHER;
                  }
                }
                """);
        compileResult.assertSuccess();

        JsonReader<Object> r = reader("TestEnum", stringReader);
        assertRead(r, "\"value1\"", enumConstant("TestEnum", "VALUE1"));
        assertRead(r, "\"value2\"", enumConstant("TestEnum", "VALUE2"));
        assertRead(r, "\"nonsense\"", enumConstant("TestEnum", "OTHER"));
        assertRead(r, "null", null);
    }

    @Test
    public void testEnumReaderFactoryIntValue() {
        compile("""
            @Json
            public enum TestEnum {
              VALUE1(1), VALUE2(2), OTHER(-1);
            
              private final int code;
              TestEnum(int code) { this.code = code; }
            
              @JsonReader
              public static TestEnum fromCode(int code) {
                for (var v : values()) { if (v.code == code) return v; }
                return OTHER;
              }
            }
            """);
        compileResult.assertSuccess();

        JsonReader<Integer> intReader = JsonParser::getIntValue;
        JsonReader<Object> r = reader("TestEnum", intReader);
        assertRead(r, "1", enumConstant("TestEnum", "VALUE1"));
        assertRead(r, "99", enumConstant("TestEnum", "OTHER"));
    }

    @Test
    public void testEnumFactoryMultipleReadersFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            @Json
            public enum TestEnum {
              VALUE1, VALUE2;
              @JsonReader public static TestEnum fromValue(String value) { return VALUE1; }
              @JsonReader public static TestEnum fromOther(String value) { return VALUE2; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("multiple @JsonReader factory"));
    }

    @Test
    public void testEnumFactoryWrongParameterCountFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            @Json
            public enum TestEnum {
              VALUE1, VALUE2;
              @JsonReader public static TestEnum fromValue(String value, int extra) { return VALUE1; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("exactly one parameter"));
    }

    @Test
    public void testEnumFactoryNonPublicFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            @Json
            public enum TestEnum {
              VALUE1, VALUE2;
              @JsonReader static TestEnum fromValue(String value) { return VALUE1; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("public static"));
    }

    @Test
    public void testEnumFactoryNonStaticFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            @Json
            public enum TestEnum {
              VALUE1, VALUE2;
              @JsonReader public TestEnum fromValue(String value) { return VALUE1; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("public static"));
    }

    private void assertRead(JsonReader<Object> reader, String json, Object expected) {
        try {
            assertThat(reader.read(json.getBytes(StandardCharsets.UTF_8))).isEqualTo(expected);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testEnumReaderFactoryTriggersWithoutJsonAnnotation() {
        compile("""
            public enum TestEnum {
              VALUE1("value1"), OTHER("other");
            
              private final String value;
              TestEnum(String value) { this.value = value; }
            
              @JsonReader
              public static TestEnum fromValue(String value) {
                return "value1".equals(value) ? VALUE1 : OTHER;
              }
            }
            """);
        compileResult.assertSuccess();

        JsonReader<Object> r = reader("TestEnum", stringReader);
        assertRead(r, "\"value1\"", enumConstant("TestEnum", "VALUE1"));
        assertRead(r, "\"x\"", enumConstant("TestEnum", "OTHER"));
    }

    @Test
    public void testJsonReaderFactoryOnNonEnumFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            public class NotAnEnum {
              private final String value;
              public NotAnEnum(String value) { this.value = value; }
              @JsonReader
              public static NotAnEnum fromValue(String value) { return new NotAnEnum(value); }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("supported only for an enum"));
    }

    @Test
    public void testEnumClassAndFactoryAnnotationUsesFactory() {
        compile("""
            @JsonReader
            public enum TestEnum {
              VALUE1("value1"), OTHER("other");
            
              private final String value;
              TestEnum(String value) { this.value = value; }
            
              @JsonReader
              public static TestEnum fromValue(String value) { return "value1".equals(value) ? VALUE1 : OTHER; }
            }
            """);
        compileResult.assertSuccess();

        JsonReader<Object> r = reader("TestEnum", stringReader);
        // @JsonReader on BOTH the enum class and the factory method: exactly one factory reader must be
        // generated. Without the within-round dedup set this crashes with a duplicate-file FilerException
        // (safeWriteTo rethrows it); without the factory short-circuit "unknown" would throw instead of OTHER.
        assertRead(r, "\"value1\"", enumConstant("TestEnum", "VALUE1"));
        assertRead(r, "\"unknown\"", enumConstant("TestEnum", "OTHER"));
    }

    @Test
    public void testEnumFactoryWrongReturnTypeFails() {
        var result = compile(List.of(new JsonAnnotationProcessor()), """
            @Json
            public enum TestEnum {
              VALUE1, VALUE2;
              @JsonReader public static String fromValue(String value) { return value; }
            }
            """);
        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("must return"));
    }

    @Test
    public void testFactoryReaderFromExtension() {
        compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              enum TestEnum {
                VALUE1("value1"), OTHER("other");
            
                private final String value;
                TestEnum(String value) { this.value = value; }
            
                @JsonReader
                public static TestEnum fromValue(String value) { return "value1".equals(value) ? VALUE1 : OTHER; }
              }
            
              default ru.tinkoff.kora.json.common.JsonReader<String> stringReader() { return com.fasterxml.jackson.core.JsonParser::getValueAsString; }
            
              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestEnum> r) { return ""; }
            }
            """);

        compileResult.assertSuccess();
        assertThat(reader("TestApp_TestEnum", stringReader)).isNotNull();
    }

    @Test
    public void testMalformedFactoryFromExtensionFails() {
        var result = compile(List.of(new KoraAppProcessor(), new JsonAnnotationProcessor()), """
            @ru.tinkoff.kora.common.KoraApp
            public interface TestApp {
              enum TestEnum {
                VALUE1, VALUE2;

                @JsonReader
                static TestEnum fromValue(String value) { return VALUE1; }
              }

              default ru.tinkoff.kora.json.common.JsonReader<String> stringReader() { return com.fasterxml.jackson.core.JsonParser::getValueAsString; }

              @Root
              default String root(ru.tinkoff.kora.json.common.JsonReader<TestEnum> r) { return ""; }
            }
            """);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.errors()).anyMatch(d -> d.getMessage(null).contains("public static"));
    }
}

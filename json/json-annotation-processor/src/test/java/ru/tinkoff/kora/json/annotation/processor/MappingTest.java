package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MappingTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    void testFinalReaderMapping() {
        compile("""
            @JsonReader
            public record TestRecord(@Mapping(FieldReader.class) String testField) {
                public static final class FieldReader implements ru.tinkoff.kora.json.common.JsonReader<String> {
            
                    @Override
                    public String read(JsonParser parser) {
                        var token = parser.currentToken();
                        if (token != JsonToken.VALUE_STRING) {
                            throw new RuntimeException();
                        }
                        return "from mapper";
                    }
                }
            
            }
            """);

        var o = reader("TestRecord").read("""
            {"testField": "testField"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", "from mapper"));

    }

    @Test
    void testNonFinalReaderMapping() {
        compile("""
            @JsonReader
            public record TestRecord(@Mapping(FieldReader.class) String testField) {
                public static class FieldReader implements ru.tinkoff.kora.json.common.JsonReader<String> {
            
                    @Override
                    public String read(JsonParser parser) {
                        var token = parser.currentToken();
                        if (token != JsonToken.VALUE_STRING) {
                            throw new RuntimeException();
                        }
                        return "from mapper";
                    }
                }
            
            }
            """);

        var o = reader("TestRecord", newObject("TestRecord$FieldReader")).read("""
            {"testField": "testField"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", "from mapper"));
    }

    @Test
    void testFinalWriterMapping() {
        compile("""
            @JsonWriter
            public record TestRecord(@Mapping(FieldWriter.class) String testField) {
                public static final class FieldWriter implements ru.tinkoff.kora.json.common.JsonWriter<String> {
            
                    @Override
                    public void write(JsonGenerator gen, String value) {
                        gen.writeString("from mapper");
                    }
                }
            
            }
            """);

        var o = writer("TestRecord").toString(newObject("TestRecord", "test"));

        assertThat(o).isEqualTo("""
            {"testField":"from mapper"}""");

    }

    @Test
    void testNonFinalWriterMapping() {
        compile("""
            @JsonWriter
            public record TestRecord(@Mapping(FieldWriter.class) String testField) {
                public static class FieldWriter implements ru.tinkoff.kora.json.common.JsonWriter<String> {
            
                    @Override
                    public void write(JsonGenerator gen, String value) {
                        gen.writeString("from mapper");
                    }
                }
            
            }
            """);

        var o = writer("TestRecord", newObject("TestRecord$FieldWriter")).toString(newObject("TestRecord", "test"));

        assertThat(o).isEqualTo("""
            {"testField":"from mapper"}""");
    }
}

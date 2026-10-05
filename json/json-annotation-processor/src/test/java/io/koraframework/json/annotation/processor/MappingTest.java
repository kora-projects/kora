package io.koraframework.json.annotation.processor;

import io.koraframework.common.annotation.Tag;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MappingTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    void testFinalReaderMapping() {
        compile("""
            @JsonReader
            public record TestRecord(@Mapping(FieldReader.class) String testField) {
                public static final class FieldReader implements io.koraframework.json.common.JsonReader<String> {
            
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
                public static class FieldReader implements io.koraframework.json.common.JsonReader<String> {
            
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
                public static final class FieldWriter implements io.koraframework.json.common.JsonWriter<String> {
            
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
                public static class FieldWriter implements io.koraframework.json.common.JsonWriter<String> {
            
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

    @Test
    void testTagWriterMapping() throws NoSuchMethodException {
        compile("""
            @JsonWriter
            public record TestRecord(@Tag(TestRecord.class) String testField) {}
            """);

        var constructor = compileResult.loadClass("$TestRecord_JsonWriter").getConstructors()[0];
        assertThat(constructor.getParameterTypes()).containsExactly(JsonWriter.class);
        assertThat(constructor.getParameters()[0].getAnnotation(Tag.class).value())
            .isEqualTo(compileResult.loadClass("TestRecord"));

        var o = writer("TestRecord", (JsonWriter<String>) (gen, value) -> gen.writeString("from tagged writer"))
            .toString(newObject("TestRecord", "test"));

        assertThat(o).isEqualTo("""
            {"testField":"from tagged writer"}""");
    }

    @Test
    void testTagReaderMapping() {
        compile("""
            @JsonReader
            public record TestRecord(@Tag(TestRecord.class) String testField) {}
            """);

        var constructor = compileResult.loadClass("$TestRecord_JsonReader").getConstructors()[0];
        assertThat(constructor.getParameterTypes()).containsExactly(JsonReader.class);
        assertThat(constructor.getParameters()[0].getAnnotation(Tag.class).value())
            .isEqualTo(compileResult.loadClass("TestRecord"));

        var o = reader("TestRecord", (JsonReader<String>) parser -> "from tagged reader").read("""
            {"testField": "testField"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", "from tagged reader"));
    }

    @Test
    void testTagPrimitiveMapping() {
        compile("""
            @Json
            public record TestRecord(@Tag(TestRecord.class) int testField) {}
            """);

        var o = reader("TestRecord", (JsonReader<Integer>) parser -> 42).read("""
            {"testField": 1}
            """);
        assertThat(o).isEqualTo(newObject("TestRecord", 42));

        var json = writer("TestRecord", (JsonWriter<Integer>) (gen, value) -> gen.writeNumber(value + 1))
            .toString(newObject("TestRecord", 1));
        assertThat(json).isEqualTo("""
            {"testField":2}""");
    }
}

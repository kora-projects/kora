package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UnboxedTest extends AbstractJsonAnnotationProcessorTest {

    JsonReader<String> stringReader = JsonParser::getValueAsString;
    JsonWriter<String> stringWriter = JsonGenerator::writeString;


    @Test
    public void unboxedRecordReader() throws IOException {
        compile(
            """
            @JsonReader
            @JsonUnboxed
            public record TestUnboxed(String a) {
            }
            """
        );

        var reader = reader("TestUnboxed", stringReader);

        assertThat(reader.read("\"test string\""))
            .isEqualTo(newObject("TestUnboxed", "test string"));
    }

    @Test
    public void unboxedRecordReaderNullableField() throws IOException {
        compile(
            """
            @JsonReader
            @JsonUnboxed
            public record TestUnboxed(@Nullable String a) {
            }
            """
        );

        var reader = reader("TestUnboxed", stringReader);

        assertThat(reader.read("null"))
            .isEqualTo(newObject("TestUnboxed", (Object) null));
    }

    @Test
    public void unboxedRecordReaderNonNullableField() {
        compile(
            """
            @JsonReader
            @JsonUnboxed
            public record TestUnboxed(String a) {
            }
            """
        );

        var reader = reader("TestUnboxed", stringReader);

        assertThatThrownBy(() -> reader.read("null"))
            .isInstanceOf(JsonParseException.class)
            .hasMessageStartingWith("Expecting nonnull value, got VALUE_NULL token");
    }

    @Test
    public void unboxedClassReader() throws IOException {
        compile(
            """
            @JsonReader
            @JsonUnboxed
            public class TestUnboxedClass {
                private final String value;
            
                public TestUnboxedClass(String value) {
                    this.value = value;
                }
            
                public String getValue() {
                    return value;
                }
            }
            """
        );

        var reader = reader("TestUnboxedClass", stringReader);

        var actual = reader.read("\"test string\"");

        assertThat(invoke(actual, "getValue"))
            .isEqualTo("test string");
    }

    @Test
    public void genericUnboxedRecordReader() {
        compile(
            """
            @JsonReader
            @JsonUnboxed
            public record TestUnboxedGeneric<T>(T value) {}
            """
        );

        var readerClass = compileResult.loadClass("$TestUnboxedGeneric_JsonReader");

        var readerClassTypeParams = Arrays.stream(readerClass.getTypeParameters()).map(TypeVariable::getName);

        assertThat(readerClassTypeParams)
            .containsExactly("T");

        var parameterTypeName = readerClass.getConstructors()[0].getParameters()[0].getParameterizedType().getTypeName();

        assertThat(parameterTypeName)
            .isEqualTo("ru.tinkoff.kora.json.common.JsonReader<T>");
    }

    @Test
    public void errorIfJsonReaderPutOnTypeWithMultipleConstructorParams() {
        var compileResult = compile(
            List.of(new JsonAnnotationProcessor()),
            """
            @JsonReader
            @JsonUnboxed
            public record TestBadValueRecord(String value1, int value2) {}
            """
        );

        assertThat(compileResult.isFailed())
            .isTrue();

        var errors = compileResult.errors();

        assertThat(errors)
            .hasSize(1);

        assertThat(errors.get(0).getMessage(Locale.getDefault()))
            .isEqualTo("@JsonUnboxed JsonReader can be created only for constructors with single parameter");
    }

    @Test
    public void readerAnnotationOnConstructor() throws IOException {
        compile(
            """
            @JsonUnboxed
            public record TestUnboxed(String a, String b) {
                @JsonReader
                public TestUnboxed(String joined) {
                    this(joined.split("\\\\."));
                }
            
                private TestUnboxed(String[] parts) {
                    this(parts[0], parts[1]);
                }
            }
            """
        );

        var reader = reader("TestUnboxed", stringReader);

        var actual = reader.read("\"test.string\"");

        var a = invoke(actual, "a");
        var b = invoke(actual, "b");

        assertThat(a)
            .isEqualTo("test");

        assertThat(b)
            .isEqualTo("string");
    }

    @Test
    public void unboxedRecordWriter() throws IOException {
        compile(
            """
            @JsonWriter
            @JsonUnboxed
            public record TestUnboxed(String a) {
            }
            """
        );

        var writer = writer("TestUnboxed", stringWriter);

        assertThat(writer.toString(newObject("TestUnboxed", "test string")))
            .isEqualTo("\"test string\"");
    }

    @Test
    public void unboxedRecordWriterNullableField() throws IOException {
        compile(
            """
            @JsonWriter
            @JsonUnboxed
            public record TestUnboxed(@Nullable String a) {
            }
            """
        );

        var writer = writer("TestUnboxed", stringWriter);

        assertThat(writer.toString(newObject("TestUnboxed", (Object) null)))
            .isEqualTo("null");
    }

    @Test
    public void unboxedClassWriter() throws IOException {
        compile(
            """
            @JsonWriter
            @JsonUnboxed
            public class TestUnboxedClass {
                private final String value;

                public TestUnboxedClass(String value) {
                    this.value = value;
                }

                public String getValue() {
                    return value;
                }
            }
            """
        );

        var writer = writer("TestUnboxedClass", stringWriter);

        var actual = writer.toString(newObject("TestUnboxedClass", "test string"));

        assertThat(actual)
            .isEqualTo("\"test string\"");
    }

    @Test
    public void genericUnboxedRecordWriter() {
        compile(
            """
            @JsonWriter
            @JsonUnboxed
            public record TestUnboxedGeneric<T>(T value) {}
            """
        );

        var readerClass = compileResult.loadClass("$TestUnboxedGeneric_JsonWriter");

        var readerClassTypeParams = Arrays.stream(readerClass.getTypeParameters()).map(TypeVariable::getName);

        assertThat(readerClassTypeParams)
            .containsExactly("T");

        var parameterTypeName = readerClass.getConstructors()[0].getParameters()[0].getParameterizedType().getTypeName();

        assertThat(parameterTypeName)
            .isEqualTo("ru.tinkoff.kora.json.common.JsonWriter<T>");
    }

    @Test
    public void errorIfJsonWriterPutOnTypeWithMultipleFields() {
        var compileResult = compile(
            List.of(new JsonAnnotationProcessor()),
            """
            @JsonWriter
            @JsonUnboxed
            public record TestBadValueRecord(String value1, int value2) {}
            """
        );

        assertThat(compileResult.isFailed())
            .isTrue();

        var errors = compileResult.errors();

        assertThat(errors)
            .hasSize(1);

        assertThat(errors.get(0).getMessage(Locale.getDefault()))
            .isEqualTo("@JsonUnboxed JsonWriter can be created only for classes with single field");
    }

    @Test
    public void noUnboxedErrorWithSkippedFields() throws IOException {
        compile(
            """
            @Json
            @JsonUnboxed
            public class TestValueClass {
                private final String value1;
                @JsonSkip
                private final int value2;

                public TestValueClass(String value1) {
                    this.value1 = value1;
                    this.value2 = value1.length();
                }

                public String getValue1() { return value1; }
                public int getValue2() { return value2; }
            }
            """
        );

        var mapper = mapper("TestValueClass", List.of(stringReader), List.of(stringWriter));

        mapper.verifyWrite(newObject("TestValueClass", "my string"), "\"my string\"");

        var readObject = mapper.read("\"test string\"");

        assertThat(invoke(readObject, "getValue1"))
            .isEqualTo("test string");

        assertThat(invoke(readObject, "getValue2"))
            .isEqualTo("test string".length());

    }
}

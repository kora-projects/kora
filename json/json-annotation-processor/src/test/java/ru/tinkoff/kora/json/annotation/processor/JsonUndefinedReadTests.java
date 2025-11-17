package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonUndefined;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUndefinedReadTests extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void jsonReaderNativeNullableIsUndefined() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.undefined()));
    }

    @Test
    public void jsonReaderNativeNullableIsPresent() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {"test_field":"test"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.of("test")));
    }

    @Test
    public void jsonReaderUserNullableIsUndefined() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<Timestamp> testField){}
            """);

        var timestampReader = new JsonReader<>() {
            @Override
            public Timestamp read(JsonParser parser) throws IOException {
                return Timestamp.from(Instant.ofEpochMilli(parser.getLongValue()));
            }
        };

        var o = reader("TestRecord", timestampReader).read("""
            {}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.undefined()));
    }

    @Test
    public void jsonReaderUserNullableIsPresent() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<Timestamp> testField){}
            """);

        var timestampReader = new JsonReader<>() {
            @Override
            public Timestamp read(JsonParser parser) throws IOException {
                return Timestamp.from(Instant.ofEpochMilli(parser.getLongValue()));
            }
        };

        var o = reader("TestRecord", timestampReader).read("""
            {"test_field":1}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.of(Timestamp.from(Instant.ofEpochMilli(1)))));
    }

    @Test
    public void jsonReaderUnknownFieldsAndNativeNullableIsUndefined() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {"f1":"1", "f2":"2"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.undefined()));
    }

    @Test
    public void jsonReaderUnknownFieldsAndNativeNullableIsPresent() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {"f1":"1", "test_field":"test", "f2":"2"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.of("test")));
    }

    @Test
    public void jsonReaderUnknownFieldsAndUserNullableIsUndefined() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<Timestamp> testField){}
            """);

        var timestampReader = new JsonReader<>() {
            @Override
            public Timestamp read(JsonParser parser) throws IOException {
                return Timestamp.from(Instant.ofEpochMilli(parser.getLongValue()));
            }
        };

        var o = reader("TestRecord", timestampReader).read("""
            {"f1":"1", "f2":"2"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.undefined()));
    }

    @Test
    public void jsonReaderUnknownFieldsAndUserNullableIsPresent() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonUndefined<Timestamp> testField){}
            """);

        var timestampReader = new JsonReader<>() {
            @Override
            public Timestamp read(JsonParser parser) throws IOException {
                return Timestamp.from(Instant.ofEpochMilli(parser.getLongValue()));
            }
        };

        var o = reader("TestRecord", timestampReader).read("""
            {"f1":"1", "test_field":1, "f2":"2"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonUndefined.of(Timestamp.from(Instant.ofEpochMilli(1)))));
    }
}

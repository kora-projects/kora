package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonNullable;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonNullableReadTests extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void jsonReaderNativeNullableIsUndefined() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonNullable<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonNullable.undefined()));
    }

    @Test
    public void jsonReaderNativeNullableIsNullable() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonNullable<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {"test_field":null}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonNullable.nullable()));
    }

    @Test
    public void jsonReaderNativeNullableIsPresent() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonNullable<String> testField){}
            """);

        var o = reader("TestRecord").read("""
            {"test_field":"test"}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonNullable.of("test")));
    }

    @Test
    public void jsonReaderUserNullableIsUndefined() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonNullable<java.sql.Timestamp> testField){}
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

        assertThat(o).isEqualTo(newObject("TestRecord", JsonNullable.undefined()));
    }

    @Test
    public void jsonReaderUserNullableIsNullable() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonNullable<java.sql.Timestamp> testField){}
            """);

        var timestampReader = new JsonReader<>() {
            @Override
            public Timestamp read(JsonParser parser) throws IOException {
                return Timestamp.from(Instant.ofEpochMilli(parser.getLongValue()));
            }
        };

        var o = reader("TestRecord", timestampReader).read("""
            {"test_field":null}
            """);

        assertThat(o).isEqualTo(newObject("TestRecord", JsonNullable.nullable()));
    }

    @Test
    public void jsonReaderUserNullableIsPresent() throws IOException {
        compile("""
            @JsonReader
            public record TestRecord(@JsonField("test_field") JsonNullable<java.sql.Timestamp> testField){}
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

        assertThat(o).isEqualTo(newObject("TestRecord", JsonNullable.of(Timestamp.from(Instant.ofEpochMilli(1)))));
    }
}

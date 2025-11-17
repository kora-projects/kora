package ru.tinkoff.kora.json.annotation.processor;

import com.fasterxml.jackson.core.JsonGenerator;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.JsonUndefined;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.ListJsonWriter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUndefinedWriteTests extends AbstractJsonAnnotationProcessorTest {

    @Test
    public void jsonWriterNativeNullableIsUndefined() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonField("test_field") JsonUndefined<String> testField){}
            """);

        var o = writer("TestRecord").toStringUnchecked(newObject("TestRecord", JsonUndefined.undefined()));

        assertThat(o).isEqualTo("""
            {}""");
    }

    @Test
    public void jsonWriterNativeNullableIsPresent() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonField("test_field") JsonUndefined<String> testField){}
            """);

        var o = writer("TestRecord").toStringUnchecked(newObject("TestRecord", JsonUndefined.of("test")));

        assertThat(o).isEqualTo("""
            {"test_field":"test"}""");
    }

    @Test
    public void jsonWriterUserNullableIsUndefined() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonField("test_field") JsonUndefined<Timestamp> testField){}
            """);

        var timeWriter = new JsonWriter<Timestamp>() {
            @Override
            public void write(JsonGenerator generator, @Nullable Timestamp object) throws IOException {
                if (object != null) {
                    generator.writeNumber(object.getTime());
                }
            }
        };

        var o = writer("TestRecord", timeWriter).toStringUnchecked(newObject("TestRecord", JsonUndefined.undefined()));

        assertThat(o).isEqualTo("""
            {}""");
    }

    @Test
    public void jsonWriterUserNullableIsPresent() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonField("test_field") JsonUndefined<Timestamp> testField){}
            """);

        var timeWriter = new JsonWriter<Timestamp>() {
            @Override
            public void write(JsonGenerator generator, @Nullable Timestamp object) throws IOException {
                if (object != null) {
                    generator.writeNumber(object.getTime());
                }
            }
        };

        var o = writer("TestRecord", timeWriter).toStringUnchecked(newObject("TestRecord", JsonUndefined.of(Timestamp.from(Instant.ofEpochMilli(1)))));

        assertThat(o).isEqualTo("""
            {"test_field":1}""");
    }

    @Test
    public void jsonWriterUserNullableNotEmptyIsEmpty() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonInclude(IncludeType.NON_EMPTY) @JsonField("test_field") JsonUndefined<List<Timestamp>> testField){}
            """);

        var timeWriter = new ListJsonWriter<>((JsonWriter<Timestamp>) (generator, object) -> {
            if (object != null) {
                generator.writeNumber(object.getTime());
            }
        });

        var o = writer("TestRecord", timeWriter).toStringUnchecked(newObject("TestRecord", JsonUndefined.of(List.of())));

        assertThat(o).isEqualTo("""
            {}""");
    }

    @Test
    public void jsonWriterUserNullableNotEmptyIsPresent() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonInclude(IncludeType.NON_EMPTY) @JsonField("test_field") JsonUndefined<List<Timestamp>> testField){}
            """);

        var timeWriter = new ListJsonWriter<>((JsonWriter<Timestamp>) (generator, object) -> {
            if (object != null) {
                generator.writeNumber(object.getTime());
            }
        });

        var o = writer("TestRecord", timeWriter).toStringUnchecked(newObject("TestRecord", JsonUndefined.of(List.of(Timestamp.from(Instant.ofEpochMilli(1))))));

        assertThat(o).isEqualTo("""
            {"test_field":[1]}""");
    }

    @Test
    public void jsonWriterUserNullableAlwaysIsEmpty() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonInclude(IncludeType.ALWAYS) @JsonField("test_field") JsonUndefined<List<Timestamp>> testField){}
            """);

        var timeWriter = new ListJsonWriter<>((JsonWriter<Timestamp>) (generator, object) -> {
            if (object != null) {
                generator.writeNumber(object.getTime());
            }
        });

        var o = writer("TestRecord", timeWriter).toStringUnchecked(newObject("TestRecord", JsonUndefined.of(List.of())));

        assertThat(o).isEqualTo("""
            {"test_field":[]}""");
    }

    @Test
    public void jsonWriterUserNullableAlwaysIsPresent() {
        compile("""
            @JsonWriter
            public record TestRecord(@JsonInclude(IncludeType.ALWAYS) @JsonField("test_field") JsonUndefined<List<Timestamp>> testField){}
            """);

        var timeWriter = new ListJsonWriter<>((JsonWriter<Timestamp>) (generator, object) -> {
            if (object != null) {
                generator.writeNumber(object.getTime());
            }
        });

        var o = writer("TestRecord", timeWriter).toStringUnchecked(newObject("TestRecord", JsonUndefined.of(List.of(Timestamp.from(Instant.ofEpochMilli(1))))));

        assertThat(o).isEqualTo("""
            {"test_field":[1]}""");
    }

}

package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.json.common.ListJsonReader;
import ru.tinkoff.kora.json.common.ListJsonWriter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.util.List;

public class JsonIncludeTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testIncludeAlways() {
        compile("""
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;

            @JsonInclude(JsonInclude.IncludeType.ALWAYS)
            @Json
            public record TestRecord(@Nullable Integer value) { }
            """
        );

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verifyWrite(newObject("TestRecord", 42), "{\"value\":42}");
        mapper.verifyWrite(newObject("TestRecord", new Object[]{null}), "{\"value\":null}");
    }

    @Test
    public void testIncludeNonNull() {
        compile("""
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;

            @JsonInclude(JsonInclude.IncludeType.NON_NULL)
            @Json
            public record TestRecord(@Nullable Integer value) { }
            """
        );

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verifyWrite(newObject("TestRecord", 42), "{\"value\":42}");
        mapper.verifyWrite(newObject("TestRecord", new Object[]{null}), "{}");
    }

    @Test
    public void testIncludeNonEmpty() {
        compile("""
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;

            @JsonInclude(JsonInclude.IncludeType.NON_EMPTY)
            @Json
            public record TestRecord(java.util.@Nullable List<Integer> value) { }
            """
        );

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord", List.of(new ListJsonReader<>(JsonParser::getIntValue)), List.of(new ListJsonWriter<Integer>(JsonGenerator::writeNumber)));
        mapper.verifyWrite(newObject("TestRecord", List.of(42)), "{\"value\":[42]}");
        mapper.verifyWrite(newObject("TestRecord", List.of()), "{}");
        mapper.verifyWrite(newObject("TestRecord", new Object[]{null}), "{}");
    }

    @Test
    public void testFieldIncludeAlways() {
        compile("""
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;

            @Json
            public record TestRecord(@Nullable String name, @JsonInclude(JsonInclude.IncludeType.ALWAYS) @Nullable Integer value) { }
            """
        );

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord");
        mapper.verifyWrite(newObject("TestRecord", "test", 42), "{\"name\":\"test\",\"value\":42}");
        mapper.verifyWrite(newObject("TestRecord", null, null), "{\"value\":null}");
    }

    @Test
    public void testFieldIncludeNonEmpty() {
        compile("""
            import ru.tinkoff.kora.json.common.annotation.JsonInclude;

            @Json
            public record TestRecord(@JsonInclude(JsonInclude.IncludeType.NON_EMPTY) java.util.@Nullable List<Integer> value) { }
            """
        );

        compileResult.assertSuccess();

        var mapper = mapper("TestRecord", List.of(new ListJsonReader<>(JsonParser::getIntValue)), List.of(new ListJsonWriter<Integer>(JsonGenerator::writeNumber)));
        mapper.verifyWrite(newObject("TestRecord", List.of(42)), "{\"value\":[42]}");
        mapper.verifyWrite(newObject("TestRecord", List.of()), "{}");
        mapper.verifyWrite(newObject("TestRecord", new Object[]{null}), "{}");
    }
}

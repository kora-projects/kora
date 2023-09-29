package ru.tinkoff.kora.json.annotation.processor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonFieldTest extends AbstractJsonAnnotationProcessorTest {
    @Test
    public void testReader() throws IOException {
        compile("""
            @Json
            public record TestRecord(@JsonField("test_field") String testField){}
            """);

        var o = reader("TestRecord").read("""
            {"test_field":"test"}""");

        assertThat(o).isEqualTo(newObject("TestRecord", "test"));
    }

    @Test
    public void testWriter() throws IOException {
        compile("""
            @Json
            public record TestRecord(@JsonField("test_field") String testField){}
            """);

        var o = writer("TestRecord").toByteArray(newObject("TestRecord", "test"));

        assertThat(o).asString(StandardCharsets.UTF_8).isEqualTo("""
            {"test_field":"test"}""");
    }

}

package ru.tinkoff.kora.json.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonEncoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class RawJsonTest {

    @Test
    void testRawJsonWriter() throws IOException {
        var rawJson = new RawJson("""
            {"key": "value"}
            """);
        var writer = new RawJsonWriter();
        var baos = new ByteArrayOutputStream();
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(baos, JsonEncoding.UTF8)) {
            writer.write(gen, rawJson);
        }
        Assertions.assertThat(rawJson.value()).isEqualTo(baos.toByteArray());
    }

    @Test
    void testRawObjectCodec() throws IOException {
        var rawJson = new RawJson("""
            {"key": "value"}
            """);

        var baos = new ByteArrayOutputStream();
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(baos, JsonEncoding.UTF8)) {
            JsonObjectCodec.write(gen, rawJson);
        }
        Assertions.assertThat(rawJson.value()).isEqualTo(baos.toByteArray());
    }
}

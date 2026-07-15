package io.koraframework.json.common;

import tools.jackson.core.exc.StreamReadException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

class JsonNumberTests extends Assertions implements JsonModule {

    private static byte[] json(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void bigDecimalReadFromNumber() throws IOException {
        var reader = bigDecimalJsonReader();

        assertEquals(new BigDecimal("123.45"), reader.read(json("123.45")));
        assertEquals(new BigDecimal("42"), reader.read(json("42")));
    }

    @Test
    void bigDecimalReadFromString() throws IOException {
        var reader = bigDecimalJsonReader();

        assertEquals(new BigDecimal("123.45"), reader.read(json("\"123.45\"")));
    }

    @Test
    void bigDecimalReadNull() throws IOException {
        var reader = bigDecimalJsonReader();

        assertNull(reader.read(json("null")));
    }

    @Test
    void bigDecimalRoundTripViaWriter() throws IOException {
        var writer = bigDecimalJsonWriter();
        var reader = bigDecimalJsonReader();
        var value = new BigDecimal("123.45");

        assertEquals(value, reader.read(writer.toByteArray(value)));
    }

    @Test
    void bigDecimalWrongTokenThrows() {
        var reader = bigDecimalJsonReader();

        assertThrows(StreamReadException.class, () -> reader.read(json("true")));
    }
}

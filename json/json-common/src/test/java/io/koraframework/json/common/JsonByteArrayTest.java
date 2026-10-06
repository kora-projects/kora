package io.koraframework.json.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamReadException;

import java.nio.charset.StandardCharsets;

class JsonByteArrayTest extends Assertions implements JsonModule {

    @Test
    void byteArrayIsWrittenAndReadAsBase64String() {
        var value = "hello".getBytes(StandardCharsets.UTF_8);

        var json = new String(byteArrayJsonWriter().toByteArray(value), StandardCharsets.UTF_8);
        assertEquals("\"aGVsbG8=\"", json);

        assertArrayEquals(value, byteArrayJsonReader().read(json));
        assertNull(byteArrayJsonReader().read("null"));
    }

    @Test
    void byteArrayReaderRejectsNonString() {
        assertThrows(StreamReadException.class, () -> byteArrayJsonReader().read("[1,2]"));
    }
}

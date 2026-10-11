package io.koraframework.http.client.common.form;

import io.koraframework.http.client.common.request.form.FormUrlEncodedWriter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormUrlEncodedWriterTest {

    @Test
    void valuesAreEncoded() throws Exception {
        try (var writer = new FormUrlEncodedWriter()) {
            writer.add("key 1", "a,b&c");
            writer.add("key2", "d");

            assertEquals("key+1=a%2Cb%26c&key2=d", body(writer));
        }
    }

    @Test
    void delimiterIsWrittenAsIsAndValuesAreEncoded() throws Exception {
        try (var writer = new FormUrlEncodedWriter()) {
            writer.add("csv", ",", List.of("a,b", "c"));
            writer.add("pipes", "|", List.of("x", "y|z"));
            writer.add("spaces", " ", List.of("one two", "three"));
            writer.add("empty", ",", List.of());

            assertEquals("csv=a%2Cb,c&pipes=x|y%7Cz&spaces=one+two%20three&empty=", body(writer));
        }
    }

    private static String body(FormUrlEncodedWriter writer) throws Exception {
        return new String(writer.write().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}

package io.koraframework.json.common;

import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamReadException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonReaderTest implements JsonModule {

    private final JsonReader<String> objectReader = parser -> {
        parser.skipChildren();
        return "object";
    };

    @Test
    void trailingScalarIsRejected() {
        var reader = integerJsonReader();
        var bytes = "1 2".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read("1 2")).isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> reader.read(bytes)).isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> reader.read(bytes, 0, bytes.length)).isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> reader.read(new ByteArrayInputStream(bytes))).isInstanceOf(StreamReadException.class);
    }

    @Test
    void trailingObjectIsRejected() {
        var bytes = "{}{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> objectReader.read("{}{}")).isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> objectReader.read(bytes)).isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> objectReader.read(bytes, 0, bytes.length)).isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> objectReader.read(new ByteArrayInputStream(bytes))).isInstanceOf(StreamReadException.class);
    }

    @Test
    void trailingWhitespaceIsAccepted() {
        var bytes = " {} \n\t ".getBytes(StandardCharsets.UTF_8);

        assertThat(integerJsonReader().read(" 1 \n\t ")).isEqualTo(1);
        assertThat(objectReader.read(bytes)).isEqualTo("object");
        assertThat(objectReader.read(bytes, 0, bytes.length)).isEqualTo("object");
        assertThat(objectReader.read(new ByteArrayInputStream(bytes))).isEqualTo("object");
    }

    @Test
    void contentOutsideOfRangeIsIgnored() {
        var bytes = "{}{}".getBytes(StandardCharsets.UTF_8);

        assertThat(objectReader.read(bytes, 0, 2)).isEqualTo("object");
    }
}

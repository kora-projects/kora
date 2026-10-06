package io.koraframework.json.common;

import io.koraframework.json.common.reader.EnumJsonReader;
import org.junit.jupiter.api.Test;
import tools.jackson.core.exc.StreamReadException;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonReadersTest implements JsonModule {

    private static void assertEndOfInputFails(JsonReader<?> reader) {
        assertThatThrownBy(() -> reader.read(new byte[0]))
            .isInstanceOf(StreamReadException.class)
            .hasMessageContaining("end of input");
    }

    @Test
    void scalarReadersFailOnEmptyInput() {
        assertEndOfInputFails(integerJsonReader());
        assertEndOfInputFails(stringJsonReader());
        assertEndOfInputFails(localDateJsonReader());
        assertEndOfInputFails(uuidJsonReader());
        assertEndOfInputFails(objectJsonReader());
        assertEndOfInputFails(listJsonReader(stringJsonReader()));
        assertEndOfInputFails(new EnumJsonReader<>(DayOfWeek.values(), Enum::name, stringJsonReader()));
    }

    @Test
    void sortedSetReaderFailsOnNullElement() {
        var reader = sortedSetJsonReader(stringJsonReader());
        assertThatThrownBy(() -> reader.read("[\"a\",null]".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(StreamReadException.class);
    }
}

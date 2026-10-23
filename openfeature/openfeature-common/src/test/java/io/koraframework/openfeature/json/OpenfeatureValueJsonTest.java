package io.koraframework.openfeature.json;

import io.koraframework.openfeature.mapper.OpenfeatureValueJsonReader;
import io.koraframework.openfeature.mapper.OpenfeatureValueJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import static org.assertj.core.api.Assertions.*;

class OpenfeatureValueJsonTest {
    private final OpenfeatureValueJsonReader reader = new OpenfeatureValueJsonReader();
    private final OpenfeatureValueJsonWriter writer = new OpenfeatureValueJsonWriter();

    @Test
    void roundTripsNestedValuesAnd64BitIntegers() {
        var json = "{\"long\":9223372036854775807,\"array\":[null,true,false,\"x\",1.5],\"nested\":{\"a\":[]}}";
        var value = reader.read(json);
        assertThat(value.asStructure().getValue("long").asLong()).isEqualTo(Long.MAX_VALUE);
        assertThat(reader.read(writer.toString(value))).isEqualTo(value);
        assertThat(reader.read(writer.toByteArray(value))).isEqualTo(value);
        assertThat(writer.toString(null)).isEqualTo("null");
        assertThat(writer.toString(reader.read("null"))).isEqualTo("null");
    }

    @Test
    void malformedTruncatedAndOverflowingInputFailsPromptly() {
        for (var input : new String[] {"", "[", "{", "[1", "{\"a\":", "{\"a\":1", "9223372036854775808"}) {
            assertThatThrownBy(() -> reader.read(input)).isInstanceOf(JacksonException.class);
        }
    }
}

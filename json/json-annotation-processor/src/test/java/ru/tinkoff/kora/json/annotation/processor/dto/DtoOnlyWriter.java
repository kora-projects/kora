package ru.tinkoff.kora.json.annotation.processor.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonSkip;
import ru.tinkoff.kora.json.common.annotation.JsonWriter;

import javax.annotation.Nullable;
import java.io.IOException;

@JsonWriter
public record DtoOnlyWriter(
    String field1,
    @JsonField("renamedField2") String field2,
    @JsonField(writer = FieldWriter.class) Inner field3,
    @JsonSkip String field4
) {

    public static final class FieldWriter implements ru.tinkoff.kora.json.common.JsonWriter<Inner> {
        @Override
        public void write(JsonGenerator gen, @Nullable Inner object) throws IOException {
            if (object == null) {
                gen.writeNull();
            } else {
                gen.writeString(object.value());
            }
        }
    }

    public record Inner(String value) {}
}

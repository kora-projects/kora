package ru.tinkoff.kora.json.annotation.processor.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.json.common.annotation.JsonField;

import java.io.IOException;

@Json
public record DtoWithJsonFieldWriter(
    @JsonField("renamedField1") String field1,
    @JsonField("renamedField2") String field2,
    @JsonField(writer = CustomWriter.class, reader = CustomReader.class) String field3,
    @JsonField(writer = CustomWriter.class, reader = CustomReader.class) String field4) {

    public static final class CustomWriter implements JsonWriter<String> {

        @Override
        public void write(JsonGenerator gen, String object) throws IOException {
            gen.writeNumber(-1);
        }
    }

    public static final class CustomReader implements JsonReader<String> {

        @Override
        public String read(JsonParser parser) throws IOException {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token == JsonToken.VALUE_NUMBER_INT) {
                return Integer.toString(parser.getIntValue());
            }
            throw new JsonParseException(parser, "expecting null or int, got " + token);
        }
    }
}

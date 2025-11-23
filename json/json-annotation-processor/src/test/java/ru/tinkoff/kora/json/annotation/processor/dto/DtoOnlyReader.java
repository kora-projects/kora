package ru.tinkoff.kora.json.annotation.processor.dto;

import ru.tinkoff.kora.json.common.annotation.JsonField;
import ru.tinkoff.kora.json.common.annotation.JsonReader;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

@JsonReader
public record DtoOnlyReader(
    String field1,
    @JsonField("renamedField2") String field2,
    @JsonField(reader = Field3Reader.class) Inner field3
) {

    public static final class Field3Reader implements ru.tinkoff.kora.json.common.JsonReader<Inner> {

        @Override
        public Inner read(JsonParser parser) {
            var token = parser.currentToken();
            if (token != JsonToken.VALUE_STRING) {
                throw new RuntimeException();
            }
            var value = parser.getValueAsString();
            return new Inner(value);
        }
    }

    public record Inner(String value) {
    }
}

package io.koraframework.json.annotation.processor.dto;

import io.koraframework.common.Mapping;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.json.common.annotation.JsonField;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

@Json
public record DtoWithJsonFieldWriter(
    @JsonField("renamedField1") String field1,
    @JsonField("renamedField2") String field2,
    @Mapping(CustomWriter.class)
    @Mapping(CustomReader.class)
    String field3,
    @Mapping(CustomWriter.class)
    @Mapping(CustomReader.class)
    @JsonField String field4) {

    public static final class CustomWriter implements JsonWriter<String> {

        @Override
        public void write(JsonGenerator gen, String object) {
            gen.writeNumber(-1);
        }
    }

    public static final class CustomReader implements JsonReader<String> {

        @Override
        public String read(JsonParser parser) {
            var token = parser.currentToken();
            if (token == JsonToken.VALUE_NULL) {
                return null;
            }
            if (token == JsonToken.VALUE_NUMBER_INT) {
                return Integer.toString(parser.getIntValue());
            }
            throw new StreamReadException(parser, "expecting null or int, got " + token);
        }
    }
}

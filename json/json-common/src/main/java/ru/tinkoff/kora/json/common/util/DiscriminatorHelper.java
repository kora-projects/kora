package ru.tinkoff.kora.json.common.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;

import javax.annotation.Nullable;
import java.io.IOException;

public class DiscriminatorHelper {
    @Nullable
    public static String readStringDiscriminator(BufferingJsonParser parser, String fieldName) throws IOException {
        var token = parser.currentToken();
        var name = new SerializedString(fieldName);
        assert token == JsonToken.START_OBJECT;
        while (!parser.nextFieldName(name)) {
            if (parser.currentToken() == JsonToken.END_OBJECT) {
                return null;
            }
            parser.skipChildren();
        }
        if (parser.nextToken() != JsonToken.VALUE_STRING) {
            throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        }
        return parser.getValueAsString();
    }
}

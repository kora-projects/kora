package ru.tinkoff.kora.json.common.util;

import jakarta.annotation.Nullable;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;

public class DiscriminatorHelper {
    @Nullable
    public static String readStringDiscriminator(BufferingJsonParser parser, String fieldName) {
        var token = parser.currentToken();
        var name = new SerializedString(fieldName);
        if (token != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "Expected start of object for discriminator field " + name);
        }
        while (!parser.nextName(name)) {
            if (parser.currentToken() == JsonToken.END_OBJECT) {
                return null;
            }
            parser.skipChildren();
        }
        if (parser.nextToken() != JsonToken.VALUE_STRING) {
            throw new StreamReadException(parser, "Expecting VALUE_STRING token, got " + parser.currentToken());
        }
        return parser.getValueAsString();
    }
}

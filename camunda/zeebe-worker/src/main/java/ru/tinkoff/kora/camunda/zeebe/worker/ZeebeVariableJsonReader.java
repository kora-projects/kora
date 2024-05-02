package ru.tinkoff.kora.camunda.zeebe.worker;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.SerializedString;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public final class ZeebeVariableJsonReader<T> implements JsonReader<T> {

    private final SerializedString fetchVariableName;
    private final boolean isNullable;
    private final JsonReader<T> valueReader;

    public ZeebeVariableJsonReader(String fetchVariableName, boolean isNullable, JsonReader<T> valueReader) {
        this.isNullable = isNullable;
        this.valueReader = valueReader;
        this.fetchVariableName = new SerializedString(fetchVariableName);
    }

    private T readValue(JsonParser parser) throws IOException {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) {
            if (isNullable) {
                return null;
            } else {
                throw new JsonParseException(parser, "Expecting NonNull value for Fetch Variable '" + fetchVariableName + "', but got VALUE_NULL token");
            }
        }
        return valueReader.read(parser);
    }

    @Override
    @Nullable
    public T read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            if (isNullable) {
                return null;
            } else {
                throw new JsonParseException(parser, "Expecting NonNull value for Fetch Variable '" + fetchVariableName + "', but got NULLABLE fetch variables");
            }
        }

        if (token != JsonToken.START_OBJECT) {
            throw new JsonParseException(parser, "Expecting START_OBJECT token, got " + token);
        }

        T value = null;
        if (parser.nextFieldName(fetchVariableName)) {
            value = readValue(parser);
            token = parser.nextToken();
            while (token != JsonToken.END_OBJECT) {
                parser.nextToken();
                parser.skipChildren();
                token = parser.nextToken();
            }
            return value;
        }

        token = parser.currentToken();
        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.FIELD_NAME) {
                throw new JsonParseException(parser, "Expecting FIELD_NAME token, got " + token);
            }
            var fieldName = parser.currentName();
            if (fieldName.equals("value")) {
                value = readValue(parser);
            } else {
                parser.nextToken();
                parser.skipChildren();
            }
            token = parser.nextToken();
        }

        return value;
    }
}

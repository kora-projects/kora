package ru.tinkoff.kora.camunda.zeebe.worker;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.json.common.JsonReader;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;

public final class ZeebeVariableJsonReader<T> implements JsonReader<T> {

    private final SerializedString fetchVariableName;
    private final boolean isNullable;
    private final JsonReader<T> valueReader;

    public ZeebeVariableJsonReader(String fetchVariableName, boolean isNullable, JsonReader<T> valueReader) {
        this.isNullable = isNullable;
        this.valueReader = valueReader;
        this.fetchVariableName = new SerializedString(fetchVariableName);
    }

    private T readValue(JsonParser parser) {
        var token = parser.nextToken();
        if (token == JsonToken.VALUE_NULL) {
            if (isNullable) {
                return null;
            } else {
                throw new StreamReadException(parser, "Expecting NonNull value for Fetch Variable '" + fetchVariableName + "', but got VALUE_NULL token");
            }
        }
        return valueReader.read(parser);
    }

    @Override
    @Nullable
    public T read(JsonParser parser) {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            if (isNullable) {
                return null;
            } else {
                throw new StreamReadException(parser, "Expecting NonNull value for Fetch Variable '" + fetchVariableName + "', but got NULLABLE fetch variables");
            }
        }

        if (token != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "Expecting START_OBJECT token, got " + token);
        }

        T value = null;
        if (parser.nextName(fetchVariableName)) {
            value = readValue(parser);
            return value;
        }

        token = parser.currentToken();
        while (token != JsonToken.END_OBJECT) {
            if (token == JsonToken.PROPERTY_NAME) {
                var fieldName = parser.currentName();
                if (fieldName.equals(fetchVariableName.getValue())) {
                    value = readValue(parser);
                    break;
                }
            }
            parser.nextToken();
            parser.skipChildren();
            token = parser.nextToken();
        }

        if (value == null && !isNullable) {
            throw new StreamReadException(parser, "Expecting NonNull value for Fetch Variable '" + fetchVariableName + "', but got NULLABLE fetch variable");
        }

        return value;
    }
}

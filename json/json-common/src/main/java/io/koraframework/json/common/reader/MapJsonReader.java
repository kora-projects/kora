package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.util.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class MapJsonReader<T> implements JsonReader<Map<String, T>> {
    private final JsonReader<T> reader;

    public MapJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public Map<String, T> read(JsonParser parser) {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "Failed to read json object: expected an object, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
        }
        token = parser.nextToken();
        if (token == JsonToken.END_OBJECT) {
            return Map.of();
        }

        Map<String, T> result = new LinkedHashMap<>();
        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                throw new StreamReadException(parser, "Failed to read json object: expected a field name, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
            }
            var fieldName = parser.currentName();
            token = parser.nextToken();
            var element = this.reader.read(parser);
            result.put(fieldName, element);
            token = parser.nextToken();
        }

        return result;
    }

    @Override
    public Map<String, T> read(JSONReader reader) {
        if (!reader.nextIfObjectStart()) {
            if (reader.wasNull()) {
                return null;
            }
            throw new JSONException(reader.info("Failed to read json object: expected an object"));
        }
        if (reader.nextIfObjectEnd()) {
            return Map.of();
        }
        String firstKey = reader.readFieldName();
        T firstValue = this.reader.read(reader);
        if (firstKey != null && firstValue != null && reader.nextIfObjectEnd()) {
            return Map.of(firstKey, firstValue);
        }

        Map<String, T> result = LinkedHashMap.newLinkedHashMap(4);
        if (firstKey != null) {
            result.put(firstKey, firstValue);
        }
        while (!reader.nextIfObjectEnd()) {
            String fieldName = reader.readFieldName();
            if (fieldName == null) {
                continue;
            }
            result.put(fieldName, this.reader.read(reader));
        }
        return result;
    }
}

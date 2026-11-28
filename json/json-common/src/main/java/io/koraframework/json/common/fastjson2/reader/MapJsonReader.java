package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.util.*;

public class MapJsonReader<T> implements JsonReader<Map<String, T>> {
    private final JsonReader<T> reader;

    public MapJsonReader(JsonReader<T> reader) {
        this.reader = reader;
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

package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.util.*;

public class ListJsonReader<T> implements JsonReader<List<T>> {
    private final JsonReader<T> reader;

    public ListJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public List<T> read(JSONReader reader) {
        if (!reader.nextIfArrayStart()) {
            if (reader.wasNull()) {
                return null;
            }
            throw new JSONException(reader.info("Failed to read json array: expected an array"));
        }
        if (reader.nextIfArrayEnd()) {
            return List.of();
        }
        var first = this.reader.read(reader);
        if (reader.nextIfArrayEnd()) {
            return List.of(first);
        }
        var result = new ArrayList<T>();
        result.add(first);
        while (!reader.nextIfArrayEnd()) {
            result.add(this.reader.read(reader));
        }
        return result;
    }
}

package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.util.*;

public class SetJsonReader<T> implements JsonReader<Set<T>> {
    private final JsonReader<T> reader;

    public SetJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public Set<T> read(JSONReader reader) {
        if (!reader.nextIfArrayStart()) {
            if (reader.wasNull()) {
                return null;
            }
            throw new JSONException(reader.info("Failed to read json array: expected an array"));
        }
        if (reader.nextIfArrayEnd()) {
            return Set.of();
        }
        T first = this.reader.read(reader);
        if (first != null && reader.nextIfArrayEnd()) {
            return Set.of(first);
        }
        Set<T> result = LinkedHashSet.newLinkedHashSet(4);
        result.add(first);
        while (!reader.nextIfArrayEnd()) {
            result.add(this.reader.read(reader));
        }
        return result;
    }
}

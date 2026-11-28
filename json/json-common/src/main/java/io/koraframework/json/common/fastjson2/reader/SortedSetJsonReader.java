package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.util.*;

public class SortedSetJsonReader<T extends Comparable<T>> implements JsonReader<SortedSet<T>> {
    private final JsonReader<T> reader;

    public SortedSetJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public SortedSet<T> read(JSONReader reader) {
        if (!reader.nextIfArrayStart()) {
            if (reader.wasNull()) {
                return null;
            }
            throw new JSONException(reader.info("Failed to read json array: expected an array"));
        }
        if (reader.nextIfArrayEnd()) {
            return Collections.emptyNavigableSet();
        }
        var result = new TreeSet<T>();
        while (!reader.nextIfArrayEnd()) {
            result.add(this.reader.read(reader));
        }
        return result;
    }
}

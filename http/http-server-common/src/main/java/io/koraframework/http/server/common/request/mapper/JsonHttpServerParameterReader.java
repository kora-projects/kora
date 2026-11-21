package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

public class JsonHttpServerParameterReader<T> implements HttpServerParameterReader<T> {
    private final JsonReader<T> reader;

    public JsonHttpServerParameterReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    @Nullable
    public T read(String string) {
        try {
            return this.reader.read(string);
        } catch (Exception e) {
            throw HttpServerResponseException.of(400, e.getMessage());
        }
    }
}

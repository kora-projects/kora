package io.koraframework.http.server.common.request.mapper;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.http.server.common.request.HttpServerParameterReader;
import io.koraframework.json.common.JsonModule;
import io.koraframework.json.common.JsonReader;
import tools.jackson.core.ObjectReadContext;

public class JsonHttpServerParameterReader<T> implements HttpServerParameterReader<T> {
    private final JsonReader<T> reader;

    public JsonHttpServerParameterReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    @Nullable
    public T read(String string) {
        try {
            return this.reader.read(JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), string));
        } catch (Exception e) {
            throw HttpServerResponseException.of(400, e.getMessage());
        }
    }
}

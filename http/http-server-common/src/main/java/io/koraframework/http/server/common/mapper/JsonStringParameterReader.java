package io.koraframework.http.server.common.mapper;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.server.common.HttpServerResponseException;
import io.koraframework.http.server.common.handler.StringParameterReader;
import io.koraframework.json.common.JsonModule;
import io.koraframework.json.common.JsonReader;
import tools.jackson.core.ObjectReadContext;

public class JsonStringParameterReader<T> implements StringParameterReader<T> {
    private final JsonReader<T> reader;

    public JsonStringParameterReader(JsonReader<T> reader) {
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

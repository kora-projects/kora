package ru.tinkoff.kora.http.server.common.mapper;

import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.StringParameterReader;
import ru.tinkoff.kora.json.common.JsonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import tools.jackson.core.ObjectReadContext;

public class JsonStringParameterReader<T> implements StringParameterReader<T> {

    private final JsonReader<T> reader;

    public JsonStringParameterReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T read(String string) {
        try {
            return this.reader.read(JsonModule.JSON_FACTORY.createParser(ObjectReadContext.empty(), string));
        } catch (Exception e) {
            throw HttpServerResponseException.of(400, e.getMessage());
        }
    }
}

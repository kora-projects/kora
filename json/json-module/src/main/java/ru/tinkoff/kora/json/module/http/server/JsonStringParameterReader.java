package ru.tinkoff.kora.json.module.http.server;

import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.StringParameterReader;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public class JsonStringParameterReader<T> implements StringParameterReader<T> {
    private final JsonReader<T> reader;

    public JsonStringParameterReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T read(String string) {
        try {
            return this.reader.read(JsonCommonModule.JSON_FACTORY.createParser(string));
        } catch (IOException e) {
            throw HttpServerResponseException.of(400, e.getMessage());
        }
    }
}

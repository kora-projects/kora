package ru.tinkoff.kora.json.module.http.client;

import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public class JsonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T> {
    private final JsonReader<T> jsonReader;

    public JsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public T apply(HttpClientResponse response) {
        try {
            return jsonReader.read(response.body().getInputStream());
        } catch (IOException e) {
            throw new HttpClientDecoderException(e);
        }
    }
}

package ru.tinkoff.kora.json.module.http.client;

import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class JsonAsyncHttpClientResponseMapper<T> implements HttpClientResponseMapper<CompletionStage<T>> {
    private final JsonReader<T> reader;

    public JsonAsyncHttpClientResponseMapper(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public CompletionStage<T> apply(HttpClientResponse response) {
        return FlowUtils.toByteArrayFuture(response.body())
            .thenApply(bytes -> {
                try {
                    return this.reader.read(bytes);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
    }
}

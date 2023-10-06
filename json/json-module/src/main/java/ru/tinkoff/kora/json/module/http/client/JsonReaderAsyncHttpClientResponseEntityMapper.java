package ru.tinkoff.kora.json.module.http.client;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class JsonReaderAsyncHttpClientResponseEntityMapper<T> implements HttpClientResponseMapper<CompletionStage<HttpResponseEntity<T>>> {
    private final JsonReader<T> jsonReader;

    public JsonReaderAsyncHttpClientResponseEntityMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Nullable
    @Override
    public CompletionStage<HttpResponseEntity<T>> apply(@Nonnull HttpClientResponse response) throws IOException, HttpClientDecoderException {
        return FlowUtils.toByteArrayFuture(response.body())
            .thenApply(bytes -> {
                try {
                    var value = this.jsonReader.read(bytes);
                    return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
    }
}

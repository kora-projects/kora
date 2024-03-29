package ru.tinkoff.kora.json.module.http.client;

import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientUnknownException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class JsonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T> {
    private final JsonReader<T> jsonReader;

    public JsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public T apply(HttpClientResponse response) throws IOException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            if (is != null) {
                return this.jsonReader.read(is);
            }
            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                return this.jsonReader.read(bytes);
            } catch (InterruptedException e) {
                throw new HttpClientUnknownException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof HttpClientException he) {
                    throw he;
                }
                if (e.getCause() != null) {
                    throw new HttpClientUnknownException(e.getCause());
                }
                throw new HttpClientUnknownException(e);
            }
        }
    }
}

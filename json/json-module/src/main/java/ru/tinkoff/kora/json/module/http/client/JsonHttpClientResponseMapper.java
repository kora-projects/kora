package ru.tinkoff.kora.json.module.http.client;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public class JsonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T, Mono<T>> {
    private final JsonReader<T> jsonReader;

    public JsonHttpClientResponseMapper(JsonReader<T> jsonReader) {
        this.jsonReader = jsonReader;
    }

    @Override
    public Mono<T> apply(HttpClientResponse response) {
        return ReactorUtils.toByteArrayMono(response.body())
            .handle((bytes, sink) -> {
                try {
                    sink.next(this.jsonReader.read(bytes));
                } catch (IOException e) {
                    sink.error(new HttpClientDecoderException(e));
                }
            });
    }
}

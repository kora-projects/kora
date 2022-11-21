package ru.tinkoff.kora.json.module.http.server;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.nio.ByteBuffer;

public class JsonWriterHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {
    private final JsonWriter<T> writer;

    public JsonWriterHttpServerResponseMapper(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public Mono<HttpServerResponse> apply(T result) {
        return Mono.fromCallable(() -> {
            var bytes = JsonWriterHttpServerResponseMapper.this.writer.toByteArray(result);
            var byteBuffer = ByteBuffer.wrap(bytes);
            return new SimpleHttpServerResponse(200, "application/json", HttpHeaders.of(), bytes.length, Flux.just(byteBuffer));
        });
    }
}

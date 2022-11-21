package ru.tinkoff.kora.json.jackson.module.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

import java.nio.ByteBuffer;

public class JacksonHttpServerResponseMapper<T> implements HttpServerResponseMapper<T> {
    private final ObjectWriter objectMapper;

    public JacksonHttpServerResponseMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        this.objectMapper = objectMapper.writerFor(objectMapper.constructType(typeRef));
    }

    @Override
    public Mono<HttpServerResponse> apply(Object result) {
        return Mono.fromCallable(() -> {
            var resultBytes = this.objectMapper.writeValueAsBytes(result);
            return new SimpleHttpServerResponse(200, "application/json", HttpHeaders.of(), resultBytes.length, Flux.just(ByteBuffer.wrap(resultBytes)));
        });
    }
}

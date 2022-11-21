package ru.tinkoff.kora.json.jackson.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;
import ru.tinkoff.kora.json.jackson.module.http.client.JacksonHttpClientRequestMapper;
import ru.tinkoff.kora.json.jackson.module.http.client.JacksonHttpClientResponseMapper;
import ru.tinkoff.kora.json.jackson.module.http.server.JacksonHttpServerRequestMapper;
import ru.tinkoff.kora.json.jackson.module.http.server.JacksonHttpServerResponseMapper;

public interface JacksonModule {
    default <T> HttpServerRequestMapper<T> jacksonHttpServerRequestMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        return new JacksonHttpServerRequestMapper<>(objectMapper, type);
    }

    default <T> HttpServerResponseMapper<T> jacksonHttpServerResponseMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        return new JacksonHttpServerResponseMapper<>(objectMapper, type);
    }

    default <T> HttpClientRequestMapper<T> jacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        return new JacksonHttpClientRequestMapper<>(objectMapper, typeRef);
    }

    default <T> HttpClientResponseMapper<T, Mono<T>> jacksonHttpClientResponseMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        return new JacksonHttpClientResponseMapper<>(objectMapper, typeRef);
    }
}

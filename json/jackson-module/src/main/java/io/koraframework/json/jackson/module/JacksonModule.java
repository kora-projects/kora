package io.koraframework.json.jackson.module;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.http.server.common.request.HttpServerRequestMapper;
import io.koraframework.http.server.common.response.HttpServerResponseMapper;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.json.jackson.module.http.client.JacksonHttpClientRequestMapper;
import io.koraframework.json.jackson.module.http.client.JacksonHttpClientResponseMapper;
import io.koraframework.json.jackson.module.http.client.JacksonReaderHttpClientResponseEntityMapper;
import io.koraframework.json.jackson.module.http.server.JacksonHttpServerRequestMapper;
import io.koraframework.json.jackson.module.http.server.JacksonHttpServerResponseMapper;
import tools.jackson.databind.ObjectMapper;

public interface JacksonModule {

    @Json
    default <T> HttpServerRequestMapper<T> jacksonHttpServerRequestMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        return new JacksonHttpServerRequestMapper<>(objectMapper, type);
    }

    @Json
    default <T> HttpServerResponseMapper<T> jacksonHttpServerResponseMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        return new JacksonHttpServerResponseMapper<>(objectMapper, type);
    }

    @Json
    default <T> HttpClientRequestMapper<T> jacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        return new JacksonHttpClientRequestMapper<>(objectMapper, typeRef);
    }

    @Json
    default <T> HttpClientResponseMapper<T> jacksonHttpClientResponseMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        return new JacksonHttpClientResponseMapper<>(objectMapper, typeRef);
    }

    @Json
    default <T> HttpClientResponseMapper<HttpResponseEntity<T>> jacksonReaderHttpClientResponseEntityMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        return new JacksonReaderHttpClientResponseEntityMapper<>(objectMapper, typeRef);
    }
}

package io.koraframework.json.jackson.module.http.client;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;

public class JacksonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T> {
    private final ObjectReader objectReader;

    private JacksonHttpClientResponseMapper(ObjectMapper objectMapper, JavaType jacksonType) {
        this.objectReader = objectMapper.readerFor(jacksonType);
    }

    public JacksonHttpClientResponseMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    public JacksonHttpClientResponseMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    @Override
    public T apply(HttpClientResponse response) throws IOException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            return this.objectReader.readValue(is);
        }
    }
}

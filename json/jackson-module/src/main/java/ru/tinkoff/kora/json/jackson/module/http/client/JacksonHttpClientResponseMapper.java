package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

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
    public T apply(HttpClientResponse response) {
        try (var body = response.body().asInputStream()) {
            return this.objectReader.readValue(body);
        } catch (Exception e) {
            throw new HttpClientDecoderException(e);
        }
    }
}

package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.client.common.HttpClientEncoderException;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;

public class JacksonHttpClientRequestMapper<T> implements HttpClientRequestMapper<T> {
    private final ObjectWriter objectWriter;

    public JacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this.objectWriter = objectMapper.writerFor(objectMapper.constructType(type));
    }

    public JacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this.objectWriter = objectMapper.writerFor(objectMapper.constructType(type));
    }

    @Override
    public HttpClientRequestBuilder apply(Request<T> request) {
        try {
            var bytes = this.objectWriter.writeValueAsBytes(request.parameter());
            return request.builder().body(bytes)
                .header("content-type", "application/json");
        } catch (JsonProcessingException e) {
            throw new HttpClientEncoderException(e);
        }
    }
}

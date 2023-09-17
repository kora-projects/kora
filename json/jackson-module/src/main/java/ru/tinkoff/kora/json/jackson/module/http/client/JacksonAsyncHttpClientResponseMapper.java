package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class JacksonAsyncHttpClientResponseMapper<T> implements HttpClientResponseMapper<CompletionStage<T>> {
    private final ObjectReader objectReader;

    private JacksonAsyncHttpClientResponseMapper(ObjectMapper objectMapper, JavaType jacksonType) {
        this.objectReader = objectMapper.readerFor(jacksonType);
    }

    public JacksonAsyncHttpClientResponseMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    public JacksonAsyncHttpClientResponseMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    @Override
    public CompletionStage<T> apply(HttpClientResponse response) {
        return FlowUtils.toByteArrayFuture(response.body())
            .thenApply(bytes -> {
                try (var p = this.objectReader.createParser(bytes)) {
                    return this.objectReader.<T>readValue(p);
                } catch (IOException e) {
                    throw new HttpClientDecoderException(e);
                }
            });
    }
}

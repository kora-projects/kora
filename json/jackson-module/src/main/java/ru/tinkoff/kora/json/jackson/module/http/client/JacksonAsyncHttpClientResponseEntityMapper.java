package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;

import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class JacksonAsyncHttpClientResponseEntityMapper<T> implements HttpClientResponseMapper<CompletionStage<HttpResponseEntity<T>>> {
    private final ObjectReader objectReader;

    public JacksonAsyncHttpClientResponseEntityMapper(ObjectMapper objectMapper, JavaType jacksonType) {
        this.objectReader = objectMapper.readerFor(jacksonType);
    }

    public JacksonAsyncHttpClientResponseEntityMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    public JacksonAsyncHttpClientResponseEntityMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    @Nullable
    @Override
    public CompletionStage<HttpResponseEntity<T>> apply(@Nonnull HttpClientResponse response) throws HttpClientDecoderException {
        return FlowUtils.toByteArrayFuture(response.body())
            .thenApply(bytes -> {
                try {
                    var value = this.objectReader.<T>readValue(bytes);
                    return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
    }
}

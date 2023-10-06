package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientUnknownException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class JacksonReaderHttpClientResponseEntityMapper<T> implements HttpClientResponseMapper<HttpResponseEntity<T>> {
    private final ObjectReader objectReader;

    private JacksonReaderHttpClientResponseEntityMapper(ObjectMapper objectMapper, JavaType jacksonType) {
        this.objectReader = objectMapper.readerFor(jacksonType);
    }

    public JacksonReaderHttpClientResponseEntityMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    public JacksonReaderHttpClientResponseEntityMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this(objectMapper, objectMapper.constructType(type));
    }

    @Nullable
    @Override
    public HttpResponseEntity<T> apply(@Nonnull HttpClientResponse response) throws IOException, HttpClientDecoderException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            if (is != null) {
                return this.objectReader.readValue(is);
            }
            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                var value = this.objectReader.<T>readValue(bytes);
                return HttpResponseEntity.of(response.code(), response.headers().toMutable(), value);
            } catch (InterruptedException e) {
                throw new HttpClientUnknownException(e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof HttpClientException he) {
                    throw he;
                }
                if (e.getCause() != null) {
                    throw new HttpClientUnknownException(e.getCause());
                }
                throw new HttpClientUnknownException(e);
            }

        }
    }
}

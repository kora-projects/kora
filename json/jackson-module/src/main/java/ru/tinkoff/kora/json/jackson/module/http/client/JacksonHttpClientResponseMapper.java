package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.HttpClientUnknownException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

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
            if (is != null) {
                return this.objectReader.readValue(is);
            }
            try {
                var bytes = body.asArrayStage().toCompletableFuture().get();
                return this.objectReader.readValue(bytes);
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

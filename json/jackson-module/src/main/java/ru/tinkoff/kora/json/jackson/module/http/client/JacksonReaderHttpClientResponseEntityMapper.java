package ru.tinkoff.kora.json.jackson.module.http.client;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;

@NullMarked
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
    public HttpResponseEntity<T> apply(HttpClientResponse response) throws IOException, HttpClientDecoderException {
        try (var body = response.body();
             var is = body.asInputStream()) {
            return this.objectReader.readValue(is);

        }
    }
}

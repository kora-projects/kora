package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;
import ru.tinkoff.kora.http.client.common.HttpClientResponseException;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.io.IOException;

public class JacksonHttpClientResponseMapper<T> implements HttpClientResponseMapper<T, Mono<T>> {
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
    public Mono<T> apply(HttpClientResponse response) {
        return ReactorUtils.toByteArrayMono(response.body())
            .handle((bytes, sink) -> {
                try (var p = this.objectReader.createParser(bytes)) {
                    var result = this.objectReader.<T>readValue(p);
                    sink.next(result);
                } catch (IOException e) {
                    sink.error(new HttpClientDecoderException(e));
                }
            });
    }
}

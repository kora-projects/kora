package ru.tinkoff.kora.json.jackson.module.http.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapper;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.json.jackson.module.http.JacksonHttpBodyOutput;

public final class JacksonHttpClientRequestMapper<T> implements HttpClientRequestMapper<T> {
    private final ObjectWriter objectWriter;

    public JacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this.objectWriter = objectMapper.writerFor(objectMapper.constructType(type));
    }

    public JacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this.objectWriter = objectMapper.writerFor(objectMapper.constructType(type));
    }

    @Override
    public HttpBodyOutput apply(Context ctx, T value) {
        return new JacksonHttpBodyOutput<>(this.objectWriter, ctx, value);
    }
}

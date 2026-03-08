package io.koraframework.json.jackson.module.http.client;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.json.jackson.module.http.JacksonHttpBodyOutput;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

public final class JacksonHttpClientRequestMapper<T> implements HttpClientRequestMapper<T> {
    private final ObjectWriter objectWriter;

    public JacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        this.objectWriter = objectMapper.writerFor(objectMapper.constructType(type));
    }

    public JacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeReference<T> type) {
        this.objectWriter = objectMapper.writerFor(objectMapper.constructType(type));
    }

    @Override
    public HttpBodyOutput apply(T value) {
        return new JacksonHttpBodyOutput<>(this.objectWriter, value);
    }
}
